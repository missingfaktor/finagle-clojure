(ns finagle-clojure.thrift
  "Functions for creating Thrift clients & servers from Java classes generated
  from a Thrift service definition using Scrooge."
  (:import [com.twitter.finagle Service Thrift]))

(defn- ^:no-doc ^String canonical-class-name
  "Take a class-name, which can be a String, Symbol or Class and returns
  the canonical class name for it (package + class).
  If class-name is a symbol the ns-imports for the current ns are checked.
  If there's no import matching the class-name symbol the symbol is returned
  as a String."
  [class-name]
  (if-let [^Class class (get (ns-imports *ns*) class-name)]
    (.getCanonicalName class)
    (if (class? class-name)
      (.getCanonicalName ^Class class-name)
      (str class-name))))

(defn ^:no-doc finagle-interface
  "Service -> 'package.canonical.Service$ServiceIface"
  [service-class-name]
  (let [canonical-service-class-name (canonical-class-name service-class-name)]
    (if (.endsWith canonical-service-class-name "$ServiceIface")
      (symbol canonical-service-class-name)
      (symbol (str canonical-service-class-name "$ServiceIface")))))

(defmacro service
  "Sugar for implementing a `com.twitter.finagle.Service` based on the
  interface defined in `qualified-service-class-name`. The appropriate
  Finagle interface for that class will automatically be imported.
  Provide an implementation for it like `proxy` (`this` is an implicit argument).

  *Arguments*:

    * `qualified-service-class-name`: This class's Finagled interface will automatically be imported.
        e.g. pass MyService not MyService$ServiceIface.
    * `body`: the implementation of this service. Methods should be defined without an explicit `this` argument.

  *Returns*:

  A new `Service`."
  [service-class-name & body]
  `(do
     (import ~(finagle-interface service-class-name))
     (proxy [~(finagle-interface service-class-name)] []
       ~@body)))

(defn serve
  "Serve `service` on `addr`. Use this to actually run your Thrift service.
  Note that this will not block while serving.
  If you want to wait on this use [[finagle-clojure.futures/await]].

  *Arguments*:

    * `addr`: The port on which to serve.
    * `service`: The `Service` that should be served.

  *Returns*:

  A new `com.twitter.finagle.ListeningServer`."
  [^String addr ^Service service]
  (Thrift/serveIface addr service))

(defmacro client
  "Sugar for creating a client for a compiled Thrift service.
  The appropriate Finagle interface for that class will automatically be imported.
  Note that this client will return a `Future` representing the result of an operation.
  This is meant to show that this client can make an RPC call and may be expensive to invoke.

  E.g. if a Thrift service definition has a method called `doStuff` you can call it on a client
  like this `(.doStuff client)`. 

  *Arguments*:

    * `addr`: Where to find the Thrift server.
    * `qualified-service-class-name`: This class's Finagled interface will automatically be imported.
        e.g. pass MyService not MyService$ServiceIface.

  *Returns*:

  A new client."
  [addr client-iterface-class]
  `(do
     (import ~(finagle-interface client-iterface-class))
     (Thrift/newIface ~addr ~(finagle-interface client-iterface-class))))
