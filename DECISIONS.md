# AI Workflow Decisions

## 1. Level 1 Planning & Architecture  
**AI Proposal:** Cursor proposed the full REST -> Service -> DAO -> Entity Slice.
**My Review:** Approved. The AI correctly identified that `FeedEntryTag` is the right conceptual model (user-scoped overlay) rather than modifying `FeedEntry` directly. It also correctly adhered to JAX-RS (`@Path`, `@POST`) and CDI (`@Singleton`) instead of defaulting to Spring Boot.

## 2. Handling Tool Lockout & Context Migration
**Issue:** While generating the Level 1 slice, Cursor IDE hit its hard token limit and locked me out, leaving files half-generated.
**Action Taken:** Instead of rolling back or forcing the tool, I kept the generated code, used the approved `PLAN.md` as a strict specification, and migrated the workflow to a web-based LLM to generate the remaining files chunk-by-chunk. This decoupled the architecture plan from the specific generation tool.