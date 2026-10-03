# Independent performance module

Shader dimension refresh, program binary cache and guarded creative logging now live in the private [muxi-performance](https://github.com/muxigame/muxi-performance) repository. Core retains world definitions, identity/permissions and asynchronous initial spawn. No performance Java classes are imported by Core; the optional NeoForge dependency orders the standalone module before Core when present.

Both old shader Mixin registrations and old logging subscribers were removed after standalone native integration. The standalone module also disables its hooks if an older Core still contains either implementation, protecting partial upgrades. Properties and default binary-cache OFF are preserved. UI=false migration is launcher commit 30a4556.

Core production Java/resources match the previously tested isolated Core candidate; only TOML metadata line endings are normalized from CRLF to LF with identical parsed metadata. The tested candidate is SHA256 6f086a998a1570f1dfca60387d0a5138c87465bbea854a9feb71253e7b703f51 (6995 assertions, native default-cache-OFF and cache-ON integration). Performance candidate 0.1.0-qa.2 SHA256 013991b69022fbe7f80528d8c3424c50eaeb87ff9fd9f0582656cc179f742f3f passed native warm integration, checksum/format-version recovery and explicit native rollback. See the performance repository native validation report for limits and failed QA fixtures.

Do not build a release from the shared dirty working tree: the paused, uncommitted PasterDream early-selection experiment remains there by request. Use an archive of the selected committed source and preserve that experiment. No parent gitlink or production deployment is changed by this extraction.
