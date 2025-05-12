(ns monkey.nats.utils
  (:require [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]))

(defn ->json
  "Converts the given structure into Json, so it can be
   passed to the various builders.  This is used as a workaround to the
   Java interop limitations, where it's not possible to invoke builder
   functions that are inherited from protected classes.  It also reduces
   the effort to map Clojure structs to the Java builders."
  [obj]
  (json/generate-string obj {:key-fn (comp csk/->camelCase name)}))

(defn method ^java.lang.reflect.Method [^java.lang.Class cl ^String n arg-types]
  (.getMethod cl n (into-array java.lang.Class arg-types)))

(defn json-method
  "Finds the `json` method of the builder object, in order to set configuration using
   a json structure.  This is useful to circumvent the problem where Clojure can't
   invoke a public method that is inherited from a package private class."
  [obj]
  (method (class obj) "json" [String]))

(defn configure-builder
  "Configures the given builder class by converting the configuration into Json and
   applying it on the builder using the `json` function.  This may be inefficient,
   but it avoids a lot of mapping work and circumvents the reflection problem
   mentioned above."
  ([b conf]
   (let [m (json-method b)]
     (-> (.invoke m b (into-array String [(->json conf)]))
         (.build))))
  ([builder appliers conf]
   (reduce-kv (fn [o k v]
                (let [a (get appliers k)]
                  (cond-> o
                    a (a v))))
              builder
              conf)))

