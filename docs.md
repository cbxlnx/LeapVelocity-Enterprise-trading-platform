# GitHub Strategies

## Branching Strategies

### Git Flow
A branching model with long-lived branches:
- `main` – production-ready code
- `develop` – integration branch for features
- `feature/*` – new features, branched from `develop`
- `release/*` – prepares a new production release
- `hotfix/*` – urgent fixes branched from `main`

### GitHub Flow
A simpler, continuous-delivery friendly model:
1. Create a branch from `main` for any change.
2. Commit changes with clear messages.
3. Open a Pull Request (PR) early for visibility/feedback.
4. Discuss, review, and iterate.
5. Merge to `main` once approved and tests pass.
6. Deploy immediately from `main`.

### Trunk-Based Development
- Developers commit small, frequent changes directly to `main` (trunk) or very short-lived feature branches.
- Feature flags are used to hide incomplete work.
- Encourages continuous integration and reduces merge conflicts.

## Commit Practices
- Write clear, descriptive commit messages (imperative mood: "Add feature" not "Added feature").
- Follow Conventional Commits when possible: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`.
- Keep commits small and focused on a single logical change.
- Squash commits before merging to keep history clean, when appropriate.

## Pull Requests (PRs)
- Keep PRs small and focused for easier review.
- Include a clear description: what changed, why, and how to test.
- Link related issues (e.g., `Closes #123`).
- Use draft PRs for work-in-progress visibility.
- Require at least one approval before merging.
- Use CI checks (tests, linting, build) as merge gates.

## Code Review Best Practices
- Review for correctness, readability, and maintainability, not just style.
- Leave constructive, specific feedback.
- Approve only when confident in the change.
- Use suggested changes for quick fixes.

## Issue Management
- Use labels (`bug`, `enhancement`, `good first issue`) to categorize.
- Use milestones to group issues for a release.
- Use GitHub Projects (Kanban boards) to track progress.
- Reference issues in commits/PRs to auto-link and close them.

## Tagging & Releases
- Use semantic versioning: `vMAJOR.MINOR.PATCH`.
- Create GitHub Releases with changelogs from tagged commits.
- Automate release notes generation where possible.

## Protecting Branches
- Enable branch protection rules on `main`:
  - Require PR reviews before merging.
  - Require status checks (CI) to pass.
  - Disallow force pushes and deletions.

## Automation (GitHub Actions)
- Automate testing, linting, and builds on every push/PR.
- Automate deployments (CD) on merge to `main` or on tag creation.
- Use workflows for issue/PR triage (labeling, stale bot, etc.).

## Collaboration Tips
- Sync forks/branches regularly with `main` to avoid large merge conflicts.
- Use `.gitignore` to keep repos clean.
- Document contribution guidelines in `CONTRIBUTING.md`.
- Use `CODEOWNERS` to auto-assign reviewers for specific paths.
