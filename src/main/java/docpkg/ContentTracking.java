package docpkg;

import docpkg.Design.BoundedContext;
import docpkg.Design.Risk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@BoundedContext
class ContentTracking {

  interface Service {
    void addWorkTree(Path path, BranchName name);

    void createBranch(BranchName name, Point point);

    CommitId commitTree(ObjectName name);

    ObjectName makeTree();
  }

  record BranchName(String value) implements Point {
  }

  record CommitId(String value) implements Point {
  }

  record ObjectName(String value) {
  }

  sealed interface Point {
    String value();
  }

  record SemanticVersion(String name, int major, int minor, int patch) {

    static Optional<SemanticVersion> from(String s) {
      var pattern = Pattern.compile(
          "([^ ]+) version (\\d+)\\.(\\d+)\\.(\\d+)(?: \\(.*\\))?"
      );
      return Optional.of(s)
          .map(pattern::matcher)
          .filter(Matcher::matches)
          .map(m -> {
            var components = Stream.of(2, 3, 4)
                .map(m::group)
                .map(Integer::parseUnsignedInt)
                .toList();
            return new SemanticVersion(
                m.group(1),
                components.get(0),
                components.get(1),
                components.get(2)
            );
          });
    }

    @Override
    public String toString() {
      return String.format("%s %d.%d.%d", name, major, minor, patch);
    }

    boolean isMetBy(SemanticVersion other) {
      return other.isCompatibleWith(this);
    }

    private boolean isCompatibleWith(SemanticVersion requirement) {
      return name.equals(requirement.name) &&
          major == requirement.major
          && minor >= requirement.minor
          && (minor > requirement.minor || patch >= requirement.patch);
    }
  }

  static class GitService implements Service {

    public static final SemanticVersion minimumVersion =
        new SemanticVersion("git", 2, 37, 0);

    private static final Logger logger =
        LoggerFactory.getLogger(GitService.class);

    public GitService() {
      if (getVersion().stream()
          .peek(v -> logger.debug("Parsed as semantic version: {}", v))
          .noneMatch(minimumVersion::isMetBy)) {
        throw new RuntimeException("Need " + minimumVersion);
      }
    }

    @Override
    public void addWorkTree(Path path, BranchName name) {
      await(command("worktree", "add", "--force", path.toString(),
          name.value())).expectSuccess();
    }

    @Override
    @Risk(scenario = "Git not configured yet for committing")
    public CommitId commitTree(ObjectName name) {
      var message = "build: new documentation package";
      var command = command("commit-tree", name.value(), "-m", message);
      return new CommitId(await(command).get().message());
    }

    @Override
    public void createBranch(BranchName name, Point point) {
      await(command("branch", name.value(), point.value()));
    }

    @Override
    @Risk(scenario = "User has no POSIX-compliant /dev/null")
    public ObjectName makeTree() {
      var nullDevice = Path.of("/dev/null").toFile();
      var command = command("mktree").redirectInput(nullDevice);
      return new ObjectName(await(command).get().message());
    }

    private <T> List<T> cons(T head, List<T> tail) {
      var result = new LinkedList<T>(tail);
      result.addFirst(head);
      return result;
    }

    private Process start(ProcessBuilder builder) {
      try {
        return builder.start();
      } catch (IOException e) {
        throw new RuntimeException("Error starting command", e);
      }
    }

    private ProcessBuilder command(String... args) {
      String programName = "git";
      var command = cons(programName, List.of(args));
      return new ProcessBuilder(command);
    }

    private String read(InputStream stream) {
      try {
        var bytes = stream.readAllBytes();
        return new String(bytes).trim();
      } catch (IOException e) {
        throw new RuntimeException("Could not read stream", e);
      }
    }

    private Result await(ProcessBuilder builder) {
      var process = start(builder);
      try {
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
          throw new RuntimeException("Process took too long");
        }
      } catch (InterruptedException e) {
        throw new RuntimeException("Interrupted while awaiting process", e);
      }
      try (var stdout = process.getInputStream();
           var stderr = process.getErrorStream()) {
        switch (process.exitValue()) {
          case 0 -> {
            return new Result.Success(read(stdout));
          }
          case 128 -> {
            return new Result.FatalApplicationError(read(stderr));
          }
          default -> {
            var message = String.format("""
                    Unexpected error code %d

                    Standard output:
                    %s

                    Standard error:
                    %s""", process.exitValue(),
                read(stdout), read(stderr));
            throw new RuntimeException(message);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException("Could not read process stream", e);
      }
    }

    private sealed interface Result {

      record Success(String message) implements Result {
      }

      record FatalApplicationError(String message) implements Result {
      }

      default Success get() {
        if (this instanceof Success s) {
          return s;
        } else {
          throw new RuntimeException("Success expectation not met");
        }
      }

      default void expectSuccess() {
        if (!(this instanceof Success s)) {
          throw new RuntimeException("Success expectation not met");
        }
      }
    }

    Optional<SemanticVersion> getVersion() {
      try {
        return SemanticVersion.from(await(command("version")).get().message());
      } catch (RuntimeException e) {
        return Optional.empty();
      }
    }
  }
}