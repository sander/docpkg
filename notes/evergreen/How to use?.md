Create a manifest file `Docpkg.toml` in your project root directory. In your continuous integration workflow, call `docpkg edit .` and `docpkg publish .`.

The `edit` command handles transclusions. This enables you to combine multiple evergreen notes into a single document. See this file’s source code for an example.

The `audit` command creates a compliance matrix. This enables you to create systems that are compliant by design. See [table/compliance.csv](table/compliance.csv) for an example and [How to track compliance data?](notes/evergreen/How%20to%20track%20compliance%20data%3F.md) for guidance.

The `publish` command publishes the three listed files from your working directory to the origin repository on branch `docpkg/my-package-name/<branch-name>`, where `<branch-name>` is the name of your current working branch. This enables easy access to documentation generated during continuous integration.

For a functional example, see the [Docpkg.toml](https://github.com/sander/docpkg/blob/main/Docpkg.toml) script and Documentation Packager’s own [published documentation](https://github.com/sander/docpkg/blob/docpkg/docpkg/main/README.md#readme). For a workflow example, see the GitHub Actions workflow [example.yml](https://github.com/sander/docpkg/blob/main/.github/workflows/example.yml) and [its runs](https://github.com/sander/docpkg/actions/workflows/example.yml).

For help, call `docpkg help`.

Note that there is no requirement to use both the `edit` and `publish` commands. For example, if you wish to only process transclusions on your `main` branch, just use `edit`.
