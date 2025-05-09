(ns monkey.nats.jetstream-test
  (:require [monkey.nats
             [jetstream :as sut]
             [test-helpers :as h]]
            [monkey.nats.jetstream.mgmt :as jsm]
            [clojure.test :refer [deftest testing is]]))

(deftest jetstream-integration
  (with-open [conn (h/make-connection)]
    (let [mgmt (jsm/make-mgmt conn)
          stream (jsm/add-stream mgmt
                                 {:name h/stream
                                  :storage-type :file
                                  :subjects ["test.*"]})
          js (sut/make-jetstream conn)
          c (jsm/make-consumer mgmt
                               stream
                               {:name "test-consumer"
                                :filter-subjects ["test.js.1"]})]
      (is (some? c))

      (is (true? (jsm/delete-stream mgmt stream))))))
