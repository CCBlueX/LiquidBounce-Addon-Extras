# Agent usage

One JSON file per change made with AI models: tokens and list-price cost per model, in the format of `schema.json`.
Add one with [agent-usage](https://github.com/1zun4/agent-usage) (`agent-usage record <session log or folder>
[--from <time>] [--to <time>] [--change <subject>]`, omp or Claude Code), or by hand as
`<yyyy-mm-dd>-<8 random hex>.json`. Never edit or delete one (`agent-usage check`).
