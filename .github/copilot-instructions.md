# Copilot Instructions — SM_Projeto_IV

## Git and GitHub Conventions

### Commit Messages

All commit messages must follow Conventional Commits.

Format:

<type>(<JIRA-KEY>): <description>

Allowed types:
- feat: new functionality
- fix: bug fix
- refactor: code restructuring without behavior change
- test: tests
- docs: documentation
- chore: maintenance/configuration
- build: build system or dependencies
- ci: CI/CD changes
- perf: performance improvement
- style: formatting/style changes without behavior change

The Jira issue key must be included whenever the commit is related
to a Jira issue.

Use imperative, concise descriptions.

Examples:

feat(SPI-15): implement tenant context middleware
fix(SPI-24): validate invalid login credentials
test(SPI-18): add tenant isolation tests
docs(SPI-11): document backend stack decision
chore(SPI-7): configure repository structure

Do not use:
- generic messages such as "update code"
- messages such as "changes"
- long descriptions in the subject
- unrelated Jira keys
- multiple unrelated changes in one commit

When asked to generate a commit message, return the commit message
only unless additional explanation is explicitly requested.

## Pull Requests

Pull Request titles must follow:

<type>(<JIRA-KEY>): <short description>

Examples:

feat(SPI-15): implement tenant context middleware
fix(SPI-24): handle invalid login credentials
docs(SPI-11): document backend architecture decision

in Portuguese BR language.



Pull Request descriptions must:
1. Identify the Jira issue.
2. Explain what was changed.
3. Explain how the change was tested.
4. Confirm the acceptance criteria.
5. Mention relevant security considerations.
6. Mention tenant isolation when applicable.

When generating a Pull Request description, use the repository's
`.github/pull_request_template.md` as the required structure.

When asked to generate a Pull Request title, return only the title
unless additional explanation is explicitly requested.