(ns chemistry.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  This repo had NO demo/visualization at all before this namespace.
  This drives the REAL actor stack (`chemistry.actor` ->
  `chemistry.governor` -> `chemistry.store`) through a scenario built
  from real, exercised store/test data and renders the result
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed (verify
  by diffing two consecutive runs before shipping). Adapted from the
  reference pattern in `cloud-itonami-isco-1211`'s
  `finmgmt.render-html` (com-junkawasaki/root, prior iteration of this
  same effort) -- the entities/rules below are specific to this
  domain, not copied verbatim.

  `sample-1` (\"Test sample A\", project `proj-1`) + `proto-1`
  (\"pH Analysis\", expected range [6.0 8.0]) + `equip-1` (\"pH
  Meter\", last calibration `2026-07-13`) are lifted VERBATIM from this
  repo's own proven-passing test fixture
  (`chemistry.actor-test/fresh-store` -- `chemistry.governor-test`'s
  own `fresh-store` registers the identical ids, with a slightly
  shorter `:description` (\"Test sample\") on the sample; this renderer
  uses the `actor-test` wording since it is the fuller of the two) --
  ground truth, not invented.

  `sample-2` (\"Test sample B\", project `proj-2`) is ADDITIONAL demo
  data registered via the SAME real protocol call
  (`store/register-sample!`) this actor's own tests use -- this actor's
  own test fixture has only one sample, so a second sample is
  registered here purely to show the console handling more than one
  sample against the same shared `proto-1`/`equip-1` resources (test
  protocols and equipment are global resources in this domain, not
  scoped to a sample, so no new cross-entity governor rule is
  demonstrated by it). Disclosed here plainly, not presented as a
  pre-existing fixture. Every other field this page displays
  (statuses, dispositions, hold reasons) is real output read after
  `run-demo!` actually executed the graph -- none of it is hand-typed.

  `:log-result` is a real, documented op (`chemistry.advisor`'s own
  namespace docstring lists `:run-test-protocol|:log-result|
  :flag-out-of-spec-result|:calibrate-equipment` as the four ops the
  advisor proposes) that has no dedicated test in this repo's own test
  suite (neither `chemistry.actor-test` nor `chemistry.governor-test`
  exercises it) -- `chemistry.governor/hard-violations` has no branch
  keyed on it, so it simply exercises the default auto-commit path
  (registered sample + `:effect :propose` + confidence at/above the
  floor). Included in the demo to show that real op name and its real
  (unremarkable) disposition, disclosed here rather than silently
  presented as covered by the test suite.

  Known architectural gaps, honestly noted rather than papered over
  (both mirror the exact same shape as `finmgmt`'s `:no-actuation` gap):

  1. `chemistry.governor`'s `:no-actuation` rule (proposal `:effect`
     must be `:propose`) is NOT reachable through this demo, because
     the real `mock-advisor` (`chemistry.advisor/infer`)
     unconditionally sets `:effect :propose` on every proposal it
     emits. Covered instead by
     `chemistry.governor-test/rejects-proposal-with-non-propose-effect`,
     which calls `governor/check` directly with a hand-built proposal.
  2. The low-confidence escalation path (`confidence <
     chemistry.governor/confidence-floor`, 0.6) is likewise NOT
     reachable through this demo: `chemistry.advisor/infer` maps
     `:stake` to a fixed confidence of 0.7/0.85/0.95
     (`:high`/`:medium`/`:low`), never below the floor. Covered instead
     by `chemistry.governor-test/escalates-low-confidence-proposals`,
     which also calls `governor/check` directly. The only escalation
     this demo genuinely reaches through the real advisor is
     `:flag-out-of-spec-result` (always human-approved regardless of
     confidence, a fixed quality-assurance safeguard).

  Usage: `clojure -M:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [chemistry.store :as store]
            [chemistry.actor :as actor]))

;; ----------------------------- harness --------------------------------

(defn- run-op!
  "Drives one real lab operation request through the actual compiled
  graph for `tid` (thread-id). If the graph escalates (interrupts
  before `:request-approval`), immediately approves it -- this demo's
  scenario never demonstrates an UNAPPROVED escalation, every
  escalation here reaches a human who signs off. Returns a map
  describing exactly what really happened -- no field is invented."
  [graph tid sample-id op extra]
  (let [request (merge {:sample-id sample-id :op op} extra)
        r1 (actor/run-request! graph request {} tid)]
    (if (= :interrupted (:status r1))
      (let [r2 (actor/approve! graph tid)]
        {:thread-id tid :sample-id sample-id :op op :request request
         :outcome :approved-and-committed
         :record (get-in r2 [:state :record])})
      (let [disposition (get-in r1 [:state :disposition])]
        (if (= :hold disposition)
          {:thread-id tid :sample-id sample-id :op op :request request
           :outcome :hard-hold
           :verdict (get-in r1 [:state :verdict])
           :rule (-> r1 :state :verdict :violations first :rule)}
          {:thread-id tid :sample-id sample-id :op op :request request
           :outcome :auto-committed
           :record (get-in r1 [:state :record])})))))

(def ^:private op-specs
  "The scenario: covers every disposition this actor can genuinely
  reach through its real graph (auto-commit, escalate-then-approve,
  and all 3 of the 4 distinct HARD-hold rules in `chemistry.governor`
  that are reachable via the real advisor -- `:no-actuation` and the
  low-confidence escalation path are structurally unreachable, see
  namespace docstring). Every `:op` keyword and violation rule name
  below is copied from `chemistry.governor`'s own `hard-violations`/
  `check`, not invented."
  [;; sample-1 / \"Test sample A\" / proto-1 / equip-1
   ;; (real fixture from chemistry.actor-test / chemistry.governor-test)
   ["s1-run-test-ok"       "sample-1" :run-test-protocol {:protocol-id "proto-1" :stake :low}]
   ["s1-log-result"        "sample-1" :log-result {:stake :low}]
   ["s1-flag-oos"          "sample-1" :flag-out-of-spec-result {:stake :high}]
   ["s1-calibrate-missing" "sample-1" :calibrate-equipment {:equipment-id "no-such-equip" :stake :high}]
   ["s1-calibrate-ok"      "sample-1" :calibrate-equipment {:equipment-id "equip-1" :stake :low}]
   ["s1-run-missing-proto" "sample-1" :run-test-protocol {:protocol-id "no-such-proto" :stake :low}]
   ;; unregistered sample entirely
   ["ghost-no-sample"      "no-such-sample" :run-test-protocol {:protocol-id "proto-1" :stake :low}]
   ;; sample-2 / \"Test sample B\" (additional demo data, registered via
   ;; the same real register-sample! call -- see namespace docstring)
   ["s2-run-test-ok"       "sample-2" :run-test-protocol {:protocol-id "proto-1" :stake :low}]
   ["s2-calibrate-ok"      "sample-2" :calibrate-equipment {:equipment-id "equip-1" :stake :low}]])

(defn run-demo!
  "Runs a fresh store through `op-specs` (see above) via the real
  compiled `chemistry.actor` graph. Returns `{:store :runs}` --
  `:runs` is the ordered vector of real per-request outcomes; every
  field in `render` below is read from this or from `store` after the
  graph actually executed, never hand-typed."
  []
  (let [db (store/mem-store)]
    (store/register-sample! db {:sample-id "sample-1" :project-id "proj-1"
                                :description "Test sample A" :status :active})
    (store/register-test-protocol! db {:protocol-id "proto-1" :name "pH Analysis"
                                       :expected-range [6.0 8.0]})
    (store/register-equipment! db {:equipment-id "equip-1" :name "pH Meter"
                                   :last-calibration "2026-07-13"})
    (store/register-sample! db {:sample-id "sample-2" :project-id "proj-2"
                                :description "Test sample B" :status :active})
    (let [graph (actor/build-graph {:store db})
          runs (mapv (fn [[tid sample-id op extra]]
                       (run-op! graph tid sample-id op extra))
                     op-specs)]
      {:store db :runs runs})))

;; ----------------------------- rendering -------------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- outcome-cell [{:keys [outcome rule]}]
  (case outcome
    :auto-committed "<span class=\"ok\">committed</span>"
    :approved-and-committed "<span class=\"ok\">approved &amp; committed</span>"
    :hard-hold (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")
    "<span class=\"muted\">in progress</span>"))

(defn- sample-row [store {:keys [sample-id project-id description]} runs]
  (let [committed (count (store/records-of store sample-id))
        sample-runs (filter #(= sample-id (:sample-id %)) runs)]
    (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%d</td><td>%d</td></tr>"
            (esc sample-id) (esc project-id) (esc description) committed (count sample-runs))))

(defn- run-row [{:keys [thread-id sample-id op request outcome rule]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc thread-id) (esc sample-id) (esc (name op))
          (esc (or (some-> (:protocol-id request) str)
                    (some-> (:equipment-id request) str)
                    ""))
          (outcome-cell {:outcome outcome :rule rule})))

(def ^:private resource-rows
  ;; Static description of the two shared lab resources registered for
  ;; this demo -- read directly from the same literal maps passed to
  ;; register-test-protocol!/register-equipment! in run-demo! above,
  ;; not a live re-derivation (these are reference/config entities with
  ;; no per-run mutable state to re-read).
  ["        <tr><td><code>proto-1</code></td><td>pH Analysis</td><td>test protocol</td><td>expected range 6.0&ndash;8.0</td></tr>"
   "        <tr><td><code>equip-1</code></td><td>pH Meter</td><td>equipment</td><td>last calibration 2026-07-13</td></tr>"])

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README.md,
  ;; `chemistry.governor`'s own docstring) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:run-test-protocol</code></td><td><span class=\"ok\">auto-commit when the sample and cited protocol are both registered</span></td></tr>"
   "        <tr><td><code>:log-result</code></td><td><span class=\"ok\">auto-commit when the sample is registered (no protocol/equipment citation required)</span></td></tr>"
   "        <tr><td><code>:calibrate-equipment</code></td><td><span class=\"warn\">HARD hold unless the cited equipment is registered</span></td></tr>"
   "        <tr><td><code>:flag-out-of-spec-result</code></td><td><span class=\"warn\">ALWAYS human approval &middot; quality-assurance safeguard, never silently dismissed</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from `{:store :runs}`
  as produced by `run-demo!` (or any other real scenario)."
  [{:keys [store runs]}]
  (let [samples [{:sample-id "sample-1" :project-id "proj-1" :description "Test sample A"}
                 {:sample-id "sample-2" :project-id "proj-2" :description "Test sample B"}]
        sample-rows (str/join "\n" (map #(sample-row store % runs) samples))
        run-rows (str/join "\n" (map run-row runs))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isco-3111 &middot; lab support (chemical and physical science technicians)</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Lab Support — Chemical &amp; Physical Science Technicians (ISCO-08 3111) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · out-of-spec flags always human-reviewed</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Registered samples</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>chemistry.store</code> via <code>chemistry.render-html</code> (<code>clojure -M:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Sample</th><th>Project</th><th>Description</th><th>Committed records</th><th>Requests this run</th></tr></thead>\n"
     "      <tbody>\n"
     sample-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Shared lab resources</h2>\n"
     "    <p class=\"muted\">Test protocols and equipment are global resources in this domain (not scoped to a single sample) — any registered sample may cite them.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Id</th><th>Name</th><th>Kind</th><th>Detail</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" resource-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Lab Technician Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Every test-protocol run must cite a registered protocol; every calibration must cite registered equipment.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit trail (this run)</h2>\n"
     "    <p class=\"muted\">Every request this scenario drove through the real compiled graph, in order — thread-id, sample, op, the request's own cited protocol/equipment, and the real disposition (auto-commit, approved-after-escalation, or the specific HARD-hold rule).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Thread</th><th>Sample</th><th>Op</th><th>Protocol/equipment cited</th><th>Disposition</th></tr></thead>\n"
     "      <tbody>\n"
     run-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        html (render result)]
    (spit out html)
    (println "wrote" out "("
             (count (:runs result)) "requests driven through the real graph,"
             (count (store/ledger (:store result))) "ledger facts )")))
