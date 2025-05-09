(ns build
  (:require [monkey.ci.build
             [api :as api]
             [v2 :as m]]
            [monkey.ci.plugin
             [clj :as clj]
             [github :as gh]]))

(defn test-job [ctx]
  ;; Take url and credentials from build params.  They are needed for integration tests
  (let [params (-> (api/build-params ctx)
                   (select-keys ["NATS_URL" "NATS_CREDS"]))]
    (-> (clj/deps-test {})
        (m/env (assoc params "NATS_STREAM" (str "test-" (get-in ctx [:build :build-id])))))))

(def deploy-job (clj/deps-publish {}))

[test-job
 deploy-job
 (gh/release-job {:dependencies ["publish"]})]
