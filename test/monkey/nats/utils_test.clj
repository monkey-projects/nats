(ns monkey.nats.utils-test
  (:require [clojure.test :refer [deftest testing is]]
            [monkey.nats.utils :as sut]))

(deftest ->json
  (testing "converts map into json with camelCased keys"
    (is (= "{\"key\":\"value\"}"
           (sut/->json {:key "value"})))))

(deftest method
  (testing "returns method by class and name with specified args"
    (let [m (sut/method io.nats.client.ConsumeOptions$Builder "group" [String])]
      (is (instance? java.lang.reflect.Method m)))))
