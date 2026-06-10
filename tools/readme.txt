tools/ - inline-allowlist.txt tuning
====================================

The build-time inliner reads inline-allowlist.txt (Class#method entries to inline before ProGuard).
Whether inlining a method helps can only be known by building and comparing size, so these scripts
search for the best list and write it to the project root. Commit the result.

Each tool is paired: .sh (Bash) and .bat (Windows) do the same thing. Run from anywhere.
Requires a working `gradlew size`.

  tune-inline    First pass. Tries each candidate alone, keeps the ones that shrink the build.
                 Slow (one build per candidate), serial. Run after changing game source.

  refine-inline  Second pass. Hill-climbs from the current list, applying the best add/remove move
                 each round until none helps - catches interactions tune-inline misses. Heavier:
                 runs N project copies in parallel. Default N=10, override with PARALLEL.

Usage
-----
  bash tools/tune-inline.sh                 tools\tune-inline.bat
  bash tools/refine-inline.sh               tools\refine-inline.bat
  PARALLEL=4 bash tools/refine-inline.sh    set PARALLEL=4 && tools\refine-inline.bat

Workflow: change source -> tune-inline -> refine-inline -> check `gradlew size` -> commit.
