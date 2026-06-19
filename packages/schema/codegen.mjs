// Codegen: single-source content schema → TS (zod) types.
// Reads specs/domain-model/schemas/content.schema.json ($defs-based) and emits
// a zod module with one schema per $def (sibling $refs resolved by passing the
// full $defs context per call). Kotlin codegen (quicktype) is added next iter.
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { jsonSchemaToZod } from "json-schema-to-zod";

const here = dirname(fileURLToPath(import.meta.url));
const schemaPath = resolve(here, "../../specs/domain-model/schemas/content.schema.json");
const schema = JSON.parse(readFileSync(schemaPath, "utf8"));
const defs = schema.$defs;

// Emit in dependency-friendly order; $refs are inlined by the tool.
const order = [
  "Provenance", "Trigger", "Action",
  "LinkPayload", "ChecklistPayload", "DocumentPayload", "MilestonePayload",
  "ContactPayload", "LocationPayload", "BudgetPayload",
  "Block", "Section", "Hub", "BriefingCard", "Place", "SyncResponse",
];

let out = `// GENERATED from specs/domain-model/schemas/content.schema.json — DO NOT EDIT.\n`;
out += `// Regenerate: npm run codegen (root). Source of truth = the JSON schema (ADR 0006).\n`;
out += `import { z } from "zod";\n\n`;

for (const name of order) {
  if (!defs[name]) throw new Error(`missing $def: ${name}`);
  // Pass the def plus full $defs so internal $refs (#/$defs/ulid, etc.) resolve.
  const sub = { ...defs[name], $defs: defs };
  const code = jsonSchemaToZod(sub, { name: `${name}Schema`, module: false, type: false });
  out += `export ${code}\n`;
  out += `export type ${name} = z.infer<typeof ${name}Schema>;\n\n`;
}

const outDir = resolve(here, "../../apps/api/src/generated");
mkdirSync(outDir, { recursive: true });
const outPath = resolve(outDir, "content.ts");
writeFileSync(outPath, out);
console.log(`codegen OK → ${outPath} (${out.length} bytes, ${order.length} types)`);
