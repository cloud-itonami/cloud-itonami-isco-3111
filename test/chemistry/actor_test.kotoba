(ns chemistry.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [chemistry.actor :as actor]
            [chemistry.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-sample! st {:sample-id "sample-1" :project-id "proj-1" :description "Test sample A" :status :active})
    (store/register-test-protocol! st {:protocol-id "proto-1" :name "pH Analysis" :expected-range [6.0 8.0]})
    (store/register-equipment! st {:equipment-id "equip-1" :name "pH Meter" :last-calibration "2026-07-13"})
    st))

(deftest commits-a-clean-low-risk-test-run
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:sample-id "sample-1" :protocol-id "proto-1" :op :run-test-protocol :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "sample-1"))))))

(deftest holds-on-unregistered-sample-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:sample-id "no-such-sample" :protocol-id "proto-1" :op :run-test-protocol :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "no-such-sample")))
    (is (= :hold (:disposition (:state result))))))

(deftest holds-on-missing-protocol-for-test-run
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:sample-id "sample-1" :protocol-id "no-such-proto" :op :run-test-protocol :stake :low}
        result (actor/run-request! graph request {} "thread-3")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "sample-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval-for-out-of-spec-flag
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; flag-out-of-spec-result always escalates (governor invariant)
        request {:sample-id "sample-1" :op :flag-out-of-spec-result :stake :high}
        interrupted (actor/run-request! graph request {} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "sample-1")))
    (let [resumed (actor/approve! graph "thread-4")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "sample-1")))))))

(deftest holds-on-missing-equipment-for-calibration
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:sample-id "sample-1" :equipment-id "no-such-equip" :op :calibrate-equipment :stake :high}
        result (actor/run-request! graph request {} "thread-5")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "sample-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest commits-calibration-on-registered-equipment
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:sample-id "sample-1" :equipment-id "equip-1" :op :calibrate-equipment :stake :low}
        result (actor/run-request! graph request {} "thread-6")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "sample-1"))))))
