# Wanda + Agro — état des lieux

Suivi du triage des 18 issues ouvertes (Wanda #23–#33, Agro #13–#19) et de leur
livraison. Deux dépôts, une seule feuille de route : presque chaque issue client
a une moitié serveur, et les deux doivent partir ensemble.

Dernière mise à jour : 2026-09-01.

---

## Fait

### Vague 0 — fusionner l'existant
Les trois PR en vol ont été fusionnées dans l'ordre (#22 → #18 → #17). `main` à
`810c035`, build et tests verts. `LibraryAlbumGrid.kt` n'existait pas avant :
les issues #24 et #25 avaient été écrites contre la branche de #22.

### Vague 1 — corrections courtes
- **#24** — le tri n'a jamais été le bug. `TrackDao` groupe par album et trie sur
  `MAX(addedTimestamp) DESC`, et `LibraryViewModel` conserve volontairement cet
  ordre. Les vrais défauts : une condition `albums.size > recentAlbums.size` qui
  cachait l'étagère pour toute bibliothèque plus petite que la limite de 12 — donc
  *toute* bibliothèque pendant sa première synchro — et une course entre les deux
  flux combinés qui reconstruisait la ligne pour une mise à jour sans changement.
- **#26** — aucune WebView ne tournait 24/7, mais aucune n'avait de démontage. Le
  nouveau `WebViewLifecycle.kt` met en pause avec le cycle de vie et détruit dans
  le bon ordre. L'importeur ne démarre plus de média non plus.
- **#15** — `Field` + `TextInput` portent le câblage d'accessibilité (`id`,
  `aria-describedby`, `aria-invalid`) ; le contrôle est un *render prop*, donc un
  site d'appel ne peut pas l'oublier. 16px et non 14px : en dessous, Safari iOS
  zoome au focus, ce qui est exactement le « clipping » signalé sur mobile.
- **#29/#33** — fusionnées en une seule issue de reconnaissance extérieure.

### Vague 2 — fiabilité de la synchro (#16 + #27)
Un canal `broadcast` jette ce qu'un abonné déconnecté n'a pas pris : un passage
Wi-Fi → cellulaire revenait en ayant silencieusement raté des trames. Chaque
message ordonné porte maintenant un `seq`, le serveur garde un tampon borné (30 s,
512 entrées) et répond à `RESUME` avec exactement ce qui a été manqué, filtré par
le même prédicat d'adressage que le flux direct. Au-delà de la fenêtre, le client
est prié de resynchroniser plutôt que de recevoir un préfixe troué.

### Vague 3 — E2EE réel sur le fil (#14 + #23)
- **Serveur** — `RelayPipe` devient `Direct | Fanout`. Une session ouverte avec un
  `jamId` diffuse un seul envoi de l'hôte vers tous les auditeurs attachés.
  L'autorisation suit l'audience : l'appartenance au jam est vérifiée à *chaque*
  attache, donc quitter le jam coupe la réception.
- **Client** — le numéro de bloc est à la fois le compteur de nonce et la donnée
  authentifiée. Les nonces ne peuvent pas se répéter tant que le compteur avance,
  et un bloc ne peut pas être déplacé ailleurs dans le flux sans échouer à
  s'authentifier. La fenêtre n'avance qu'après authentification, pour qu'un numéro
  forgé ne puisse pas verrouiller le bloc légitime qui suit.
- **Clé** — HKDF depuis une clé de salon par piste, distribuée via le `sealNote`
  existant : 32 octets scellés par membre, une fois par piste. C'est ce qui
  préserve la propriété visée — l'hôte chiffre une fois pour tout le salon.

### Vague 4 — identité canonique (#28 + #17)
- **Pas de NDK.** La comparaison ne se fait jamais contre le catalogue AcoustID,
  seulement contre des empreintes calculées par cette flotte : la compatibilité
  binaire avec Chromaprint n'apporte rien et coûterait une chaîne de compilation
  native, une bibliothèque C++ vendorisée et un empreinteur non testable sur la JVM.
- **Mesures**, pas suppositions :

  | Cas | Similarité de séquence | Sous-hachages exacts communs |
  |---|---|---|
  | Même audio | 1.00 | 174/174 |
  | Gain ×0.35 | 0.997 | 160/174 |
  | Bruit faible (~66 dB SNR) | 0.73 | **0/174** |
  | Bruit (~54 dB SNR) | 0.62 | **0/174** |
  | Enregistrement différent | 0.50 (hasard) | 0/174 |

- **La recherche exacte sur 32 bits ne marche pas** : un seul bit inversé change
  toute la valeur. C'était la prémisse de #28 et #17, et elle est fausse. L'index
  porte donc sur des **moitiés de 16 bits** — une copie dégradée conserve 10 à 38
  correspondances exactes sur ~200, contre **1** pour un enregistrement sans rapport.
- **Catalogue partagé** — local d'abord, serveur optionnel. `publishRecording` et
  `catalogSince(curseur)`. Les métadonnées se complètent, ne s'écrasent jamais.
  Authentifié mais non restreint à l'appelant : le catalogue contient des faits sur
  des enregistrements et rien sur qui a écouté quoi.
- Les deux empreintes sortent d'un **seul décodage** dans le worker existant.

### Vague 4bis — les empreintes servent enfin à quelque chose
Tout ce qui précède se calculait sans rien changer à l'application : `matchesFor()`
et `sync()` n'avaient aucun appelant. Désormais :
- **Liens d'enregistrement.** `FingerprintIndexWorker` interroge le comparateur juste
  après avoir indexé une piste et écrit le verdict dans `recording_links`.
  `RecordingLinkSet` — le pendant positif de `SplitSet`, même forme, toujours passé
  *dans* `TrackDeduplicator` plutôt que lu par lui — rejoint les groupes que les
  métadonnées ne pouvaient pas rapprocher. Ordre d'autorité : une épingle refuse,
  puis un lien rassemble, puis les règles de métadonnées tranchent. Un lien franchit
  aussi la tolérance de durée : l'audio a répondu à la question qu'elle approximait.
- **Métadonnées canoniques.** Le catalogue ne compte plus ses correspondances pour les
  jeter. `canonical_metadata` garde ce qu'il a appris, hors de `tracks` que
  `TrackSourceFields` réécrit à chaque resynchro, et `applyToLibrary()` les repose à
  chaque passage — c'est autant une réparation qu'une livraison. `CanonicalMetadataMerge`
  porte la règle : on complète un champ vide, ou on retire la décoration d'un titre par
  ailleurs identique. Rien d'autre. Les marqueurs de variante sont comparés séparément,
  sinon « (Live) » disparaissait au profit de la version studio.
- **`CatalogSyncRepository.sync()`** est appelé par `LibrarySyncWorker`, après la
  réconciliation et avant la notification. Son résultat est volontairement ignoré : sans
  serveur il répond `NOT_CONFIGURED`, et un catalogue injoignable est une optimisation
  ratée, pas une synchro échouée.
- **Réindexation côté Agro.** `Db::reindex_normalisation` recalcule `norm_*` pour
  `library_tracks` **et** `playlist_items` — les deux, parce que `norm.rs` existe pour
  qu'une seule convention règne. Bornée et reprenable (`done`), exposée en mutation
  `reindexNormalisation` réservée aux admins. `updated_at` n'est pas touché : rien du
  fichier n'a changé, seul l'index dérivé du serveur.

---

## Reste à faire

### Bloquant avant de continuer
1. **Tester #35 sur deux appareils réels.** La couture Media3 n'est couverte par
   aucun test : `RelayDecryptingDataSource` n'est exercé nulle part, et je n'ai pas
   pu lancer de lecture. Le chemin des en-têtes, l'envoi sans `Content-Length` et
   la fin de flux sont les points qui mordront.

### Vague 5 — découverte
2. **Agro #18 + Wanda #30** — vecteurs acoustiques et radio KNN.
3. **Agro #19 + Wanda #31** — compteurs aveuglés et étagère « Popular on Agro ».
   Côté client, c'est petit : `AgroLibraryApi.popularTracks()`, intégration dans
   `RecommendationRepository`, une constante dans `HomeViewModel.SectionOrder`.
   Aucune UI nouvelle (`TRACK_CAROUSEL` existe déjà).
4. **Wanda #33 + #29 fusionnées** — reconnaissance extérieure. Il reste le moteur
   B : suivi de hauteur YIN, entité de contour mélodique, appariement DTW, puis
   refonte de `RecognitionRepository` pour que les deux moteurs consomment une
   seule capture micro et alimentent une seule liste classée.

### Vague 6 — les gros morceaux
5. **Wanda #32** — transport hors-réseau Wi-Fi Direct / BLE / LocalOnlyHotspot.
   Entièrement absent aujourd'hui : zéro occurrence de `WifiP2pManager`,
   `NsdManager`, BLE ou mDNS. À construire derrière l'abstraction `ResolvedFrom`
   existante pour que `ListenAlongResolver` gagne un palier au lieu d'être réécrit.
   À découper en épopée : découverte BLE + poignée de main X25519, montée en
   Wi-Fi Direct, pair PC.
6. **Wanda #25** — la feuille d'actions album est petite, mais les liens
   universels inter-sources sont une vraie fonctionnalité. `ShareKind.ALBUM`
   existe déjà ; le blocage est que `AlbumCard` n'a pas d'appui long, et qu'il
   n'existe aucun lien « Wanda » agnostique — `ShareRepository` ne fait que
   relayer le lien forgé par chaque backend.

### Indépendant
7. **Agro #13** — CrowdSec tourne **devant** Agro, pas dedans. Deux parties :
   donner aux vérifications 2FA leur propre seau de limitation dans `login.rs`
   avec des codes HTTP distincts, puis fournir la configuration de parseur
   CrowdSec en artefact de déploiement. À noter : `audit.rs` tronque les IP en
   /24-/64 par conception, donc c'est le proxy et non Agro qui décide par IP.
   L'issue mentionne aussi WebAuthn/FIDO2 : Agro n'a que TOTP, rien pour WebAuthn.

---

## Bug ouvert, hors vagues

**« Memories » joue toujours le même morceau.** Diagnostic partiel :
`TrackDeduplicator` inclut l'artiste dans sa clé et exige des durées à 3 s près, donc
un repli sur le titre seul ne l'explique pas. Deux pistes restent : des métadonnées
d'artiste vides ou fausses, ou — plus probable puisque le symptôme est à la lecture
et non à l'affichage — le chemin de résolution qui choisit une source par recherche
titre/artiste (`bestMatch`, `renditionsOf`), plus laxiste que le déduplicateur.

Côté Agro il n'y a **rien à réinitialiser** : `library_tracks` a `content_hash` en
clé primaire, donc deux fichiers différents sont toujours deux lignes ; les colonnes
`norm_*` ne servent qu'aux requêtes de correspondance. Les empreintes canoniques
corrigeront la cause 1 pour l'avenir, mais pas rétroactivement, et pas la cause 2.
`RecordingSplitRepository.keepApart()` est déjà le recours manuel.

---

## Décisions prises en route

- **Trois empreintes, deux sous-systèmes.** Points de repère (micro, existant) et
  contour mélodique (fredonnement, à faire) partagent une seule capture et une
  seule liste de résultats ; Chromaprint sert l'identité canonique et ne touche
  jamais le micro.
- **Pas de trames d'acquittement** malgré #16 : reprendre depuis une position les
  englobe. Le client sait déjà ce qu'il a reçu.
- **Pas de file d'attente sortante** malgré #27 : le client n'envoie rien sur la
  socket à part `AUTH`. C'eût été du code mort.
- **Une épingle bat un lien.** Le comparateur d'empreintes se trompe rarement, mais
  quand il se trompe c'est sur un fichier mal étiqueté — exactement le cas où
  l'utilisateur voudra protester. Si un lien primait sur `RecordingSplitRepository`,
  « ce n'est pas le même enregistrement » deviendrait un bouton sans effet. Et
  l'épingle refuse la fusion du **groupe entier**, pas seulement de sa paire : les
  groupes sont repliés sur une ligne en aval, donc fusionner via une autre paire
  remettrait ensemble les deux lignes qu'on venait de séparer.
- **Le catalogue complète, il ne renomme pas.** Une entrée vient d'un autre appareil
  faisant tourner les mêmes importeurs sur des étiquettes tout aussi imparfaites :
  « le catalogue dit autrement » ne prouve rien. Il remplit un champ vide, ou retire
  la décoration d'un titre par ailleurs identique. Rien d'autre.
- **`lastSeq` en mémoire**, pas dans `SecureStorage` : il n'a de sens que face à un
  serveur qui a encore les messages en tampon, ce qu'il n'aura plus après une mort
  de processus.
