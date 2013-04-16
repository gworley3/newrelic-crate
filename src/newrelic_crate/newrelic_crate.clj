;; TODO edit this with links to the software being installed and configured
(ns newrelic-crate.newrelic-crate
  "A [pallet](https://palletops.com/) crate to install and configure newrelic-crate"
  [pallet.action :refer [with-action-options]]
  [pallet.actions :refer [directory exec-checked-script remote-directory
                          remote-file]
                  :as actions]
  [pallet.api :refer [plan-fn] :as api]
  [pallet.crate :refer [assoc-settings defmethod-plan defplan get-settings]]
  [pallet.crate-install :as crate-install]
  [pallet.stevedore :refer [fragment]]
  [pallet.script.lib :refer [config-root file]]
  [pallet.utils :refer [apply-map]]
  [pallet.version-dispatch :refer [defmethod-version-plan
                                   defmulti-version-plan]])

;;; # Settings
(defn default-settings
  "Provides default settings, that are merged with any user supplied settings."
  []
  ;; TODO add configuration options here
  {:user "newrelic-crate"
   :group "newrelic-crate"
   :owner "newrelic-crate"
   :config-dir (fragment (file (config-root) "newrelic-crate"))})

(defmulti-version-plan settings-map [version settings])

(defmethod-version-plan
    settings-map {:os :linux}
    [os os-version version settings]
  (cond
   (:install-strategy settings) settings
   :else (assoc settings
           :install-strategy :packages
           :packages {:apt ["newrelic-crate"]
                      :aptitude ["newrelic-crate"]
                      :yum ["newrelic-crate"]
                      :pacman ["newrelic-crate"]
                      :zypper ["newrelic-crate"]
                      :portage ["newrelic-crate"]
                      :brew ["newrelic-crate"]})))

(defplan settings
  "Settings for newrelic-crate"
  [{:keys [instance-id] :as settings}]
  (let [settings (merge (default-settings) settings)
        settings (settings-map (:version settings) settings)]
    (assoc-settings :newrelic-crate settings {:instance-id instance-id})))

;;; # User
(defplan user
  "Create the newrelic-crate user"
  [{:keys [instance-id] :as options}]
  (let [{:keys [user owner group home]} (get-settings :newrelic-crate options)]
    (actions/group group :system true)
    (when (not= owner user)
      (actions/user owner :group group :system true))
    (actions/user
     user :group group :system true :create-home true :shell :bash)))

;;; # Install
(defplan install
  "Install newrelic-crate"
  [& {:keys [instance-id]}]
  (let [settings (get-settings :newrelic-crate {:instance-id instance-id})]
    (crate-install/install :newrelic-crate instance-id)))

;;; # Configure
(def ^{:doc "Flag for recognising changes to configuration"}
  newrelic-crate-config-changed-flag "newrelic-crate-config")

(defplan config-file
  "Helper to write config files"
  [{:keys [owner group config-dir] :as settings} filename file-source]
  (directory config-dir :owner owner :group group)
  (apply
   remote-file (str config-dir "/" filename)
   :flag-on-changed newrelic-crate-config-changed-flag
   :owner owner :group group
   (apply concat file-source)))

(defplan configure
  "Write all config files"
  [{:keys [instance-id] :as options}]
  (let [{:keys [] :as settings} (get-settings :newrelic-crate options)]
    (config-file settings "newrelic-crate.conf" {:content (str config)})))

;;; # Server spec
(defn server-spec
  "Returns a server-spec that installs and configures newrelic-crate."
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases
   {:settings (plan-fn
                (newrelic_crate/newrelic_crate/settings (merge settings options)))
    :install (plan-fn
              (user options)
              (install :instance-id instance-id))
    :configure (plan-fn
                 (config options)
                 (run options))}))
