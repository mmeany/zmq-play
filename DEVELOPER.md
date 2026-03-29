# Release Process Documentation

This project uses GitHub Actions to automate the release process. Releases are categorized into Major, Minor, and Patch versions following Semantic Versioning (SemVer) principles.

## 1. Major and Minor Releases

Major and Minor releases are performed directly from the `main` branch.

### Steps:
1.  Ensure all changes intended for the release are merged into the `main` branch.
2.  Navigate to the **Actions** tab in the GitHub repository.
3.  Select the **Release (Major/Minor)** workflow from the sidebar.
4.  Click the **Run workflow** dropdown button.
5.  Select **main** as the branch.
6.  Choose the **release type**:
    *   **major**: Increments the first digit (e.g., `v1.2.0` -> `v2.0.0`).
    *   **minor**: Increments the second digit (e.g., `v1.2.0` -> `v1.3.0`).
7.  Click **Run workflow**.

### What happens:
*   The workflow calculates the next version based on the latest tag.
*   The patch version is automatically reset to `0`.
*   A new git tag is created (e.g., `v2.0.0`).
*   A GitHub Release is published with the built JAR artifacts.

---

## 2. Patch Releases

Patch releases are used for bug fixes and are performed from dedicated patch branches to allow for backporting.

### Steps:
1.  Create a new branch from `main` or the relevant release tag. The branch name **must** start with `patch/` (e.g., `patch/fix-memory-leak`).
2.  Commit your fixes to this branch.
3.  Push the branch to GitHub.
4.  Navigate to the **Actions** tab.
5.  Select the **Release (Patch)** workflow.
6.  Click **Run workflow**.
7.  Select your **patch branch** (e.g., `patch/fix-memory-leak`) in the "Use workflow from" dropdown.
8.  Click **Run workflow**.

### What happens:
*   The workflow calculates the next patch version (e.g., `v1.3.0` -> `v1.3.1`).
*   A new git tag is created.
*   A GitHub Release is published with the built JAR artifacts.
*   **Automated Backport**: A Pull Request is automatically created to merge the patch fixes back into the `main` branch to prevent regression.

---

## 3. Post-Release Verification

After any release:
*   **Verify Artifacts**: Check the "Releases" section of the repository to ensure the `.jar` files were correctly uploaded.
*   **Merge Backports**: For patch releases, a developer must review and merge the automated "Backport" PR into `main`.
*   **Permissions**: To run these workflows, you must have `write` access to the repository. The workflows use the built-in `GITHUB_TOKEN` which is pre-configured with the necessary permissions to create releases and PRs.

## 4. Troubleshooting

*   **Version mismatch**: If the version is not what you expected, ensure you have fetched all tags locally (`git fetch --tags`) and that the latest tag in the repository is correct.
*   **Workflow fails**: Check the logs in the **Actions** tab. Common failures include missing permissions or build errors in the Java code.
