import { describe, it, expect } from "vitest";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import {
  migrationFiles,
  versionOf,
  checksumOf,
  pendingMigrations,
} from "../scripts/migrate.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const migDir = resolve(here, "../migrations");

// ADR 0033 runner — the pure planning logic (no DB). The apply/record path is
// exercised against a real Postgres in deploy; here we lock the file-selection
// + version/checksum logic that decides WHAT runs.
describe("migration runner (ADR 0033)", () => {
  it("lists the real migrations sorted, only .sql, including the tracking table", () => {
    const files = migrationFiles(migDir);
    expect(files).toEqual([...files].sort()); // forward-only order
    expect(files.every((f) => f.endsWith(".sql"))).toBe(true);
    expect(files[0]).toBe("0001_m0_init.sql");
    expect(files).toContain("0012_schema_migrations.sql");
  });

  it("versionOf strips the .sql extension", () => {
    expect(versionOf("0012_schema_migrations.sql")).toBe("0012_schema_migrations");
    expect(versionOf("0001_m0_init.sql")).toBe("0001_m0_init");
  });

  it("checksum is deterministic and content-sensitive", () => {
    expect(checksumOf("CREATE TABLE x()")).toBe(checksumOf("CREATE TABLE x()"));
    expect(checksumOf("a")).not.toBe(checksumOf("b"));
    expect(checksumOf("a")).toMatch(/^[0-9a-f]{64}$/); // sha256 hex
  });

  it("pending = files not yet applied, in order (re-run is empty)", () => {
    const files = ["0001_a.sql", "0002_b.sql", "0003_c.sql"];
    expect(pendingMigrations(files, []).map(versionOf)).toEqual(["0001_a", "0002_b", "0003_c"]);
    expect(pendingMigrations(files, ["0001_a", "0002_b"]).map(versionOf)).toEqual(["0003_c"]);
    expect(pendingMigrations(files, ["0001_a", "0002_b", "0003_c"])).toEqual([]); // up to date
  });

  it("an already-applied middle migration is skipped without reordering the rest", () => {
    const files = ["0001_a.sql", "0002_b.sql", "0003_c.sql"];
    expect(pendingMigrations(files, ["0002_b"]).map(versionOf)).toEqual(["0001_a", "0003_c"]);
  });
});
