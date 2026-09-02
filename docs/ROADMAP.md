# Wanda + Agro — état des lieux

Suivi du triage des 18 issues ouvertes (Wanda #23–#33, Agro #13–#19) et de leur
livraison. Deux dépôts, une seule feuille de route : presque chaque issue client
a une moitié serveur, et les deux doivent partir ensemble.

Dernière mise à jour : 2026-09-01 (soirée).

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

### Vague 4ter — la session P2P : ce qui a été réparé

Une soirée entière sur deux téléphones. La plupart des pannes n'étaient pas là où on les
cherchait, et plusieurs étaient masquées les unes par les autres.

**L'UUID de service n'était pas hexadécimal.** `0000w4nd-…` épelait un mot, `ParcelUuid.fromString`
levait dans le constructeur de `BleDiscovery`, et comme la classe est un `@Singleton` ça emportait
tout le graphe Hilt : l'app crashait au lancement. Remplacé par un UUID 128 bits aléatoire.

**Aucune piste n'avait de `contentHash`.** Il n'était calculé que par `LibrarySyncWorker`
(*unmetered + charging*), donc sur un appareil qui n'avait jamais fini une synchro, aucun fichier
n'en avait. Or les trois paliers pairs demandent des octets **par hash**. Une seule cause pour trois
symptômes : « couldn't find song » en listen-along, « you don't have [titre] » en jam, et pause/skip
qui ne se propageaient pas — la résolution échouant, `playingKey` restait nul et chaque trame
repartait dans la résolution au lieu d'ajuster le transport. `SharedTrackHash` calcule désormais le
hash du fichier au moment où son propriétaire le partage.

**Une ligne fantôme empoisonnait Room.** Les pistes relay/p2p sont déclarées `source = LOCAL`, et
l'une d'elles avait été persistée : `id = relay:…`, pas de fichier, et une URL de session relay
morte dans `streamUri`. Le tier 1 la trouvait par titre avant le vrai fichier et rendait un `401`,
définitivement et pour ce titre seulement. `TrackDao.upsertTracks` refuse ces ids, et
`getStreamInfo` supprime la ligne quand il en croise une.

**Le relay était ouvert deux fois.** `RelayDecryptingDataSource` ouvrait la source pour lire les
en-têtes puis passait le même `DataSource` à `DataSourceInputStream`, qui l'ouvre lui-même à la
première lecture. Une session relay ne sert sa moitié réceptrice qu'une fois : la seconde requête
répondait `409`. Corrigé par `OpenDataSourceStream`. Et `StreamResolver` marque enfin ces flux
non cachables — `RelayDecryptingDataSource` documentait cette propriété alors qu'aucun drapeau de
cache n'existait dans le projet, si bien que `CacheDataSource` rouvrait la source pour remplir
chaque span *et* écrivait le morceau emprunté sur le disque.

**`ACCESS_LOCAL_NETWORK` n'était jamais demandée.** Déclarée au manifeste, jamais requise, donc
refusée en silence sur Android 16+. Le téléphone ne pouvait ni ouvrir ni accepter une connexion
LAN, dans les deux sens, pendant que la library sync marchait très bien parce qu'elle sort vers
l'IP publique. `LocalNetworkGate` existait mais n'était branché que sur l'offre de sync ; il l'est
maintenant sur le listen-along et sur la création/jonction de jam.

**Le partage faisait planter celui qui partage.** `P2PServer` lançait `handleClient` dans un scope
sans `CoroutineExceptionHandler`, et seul le chemin audio était protégé. Le *broken pipe* que
produit ExoPlayer à chaque seek côté auditeur tuait le processus de l'hôte.

**Notes en clair.** `FriendEntity` n'a pas de colonne `publicKey`, donc `toProfile()` la laissait
nulle et *aucune* note n'a jamais pu être scellée. La clé est désormais récupérée fraîche à
l'envoi, ce qui est de toute façon la bonne forme : une clé périmée scelle une note que le
destinataire ne peut plus ouvrir.

**Le pause/dépause en boucle du listen-along.** `followerSetPlaying` comparait `isPlaying`, qui est
faux pendant tout un buffer, au lieu de `playWhenReady` : chaque trame de l'hôte ressemblait à un
appareil qui n'avait pas démarré et réémettait la reprise. Et `correctDrift` n'avait pas la garde
de buffering que `JamPlaybackController` possède et documente — le même piège trouvé d'un côté
seulement.

**Repeat et shuffle en jam.** Non gardés, contrairement à pause/seek/next. `REPEAT_MODE_ONE`
relançait la piste pendant que la salle avançait, le réconciliateur mesurait la longueur du morceau
comme dérive et corrigeait sans fin. Ils sont maintenant inertes et grisés
(`PlaybackState.orderLocked`).

### Vague 4quater — ce qui a été construit

- **Écran off-grid** (`OffGridScreen`), atteint depuis l'en-tête Friends, **hors** du gate
  `isPaired` puisque c'est la fonction qui doit marcher sans serveur.
- **Poignée de main locale** : `GET /p2p/pair?key=…` mint un grant de 256 bits et le rend **scellé**
  à la clé du demandeur, qui vérifie ensuite l'empreinte du beacon. Le tier 5 ne dépend donc plus
  d'Agro pour son jeton — c'était le trou qui rendait l'off-grid impossible sans serveur.
- **Pré-buffer** des deux premières secondes de la piste suivante, réglable, avec `PreloadDecision`
  qui refuse les ids à usage unique (précharger une session relay la consomme) et les directs.
- **Empreintes des sources streamées** : `PcmDecoder` accepte une URL avec en-têtes, et les **deux**
  requêtes SQL qui filtraient indépendamment sur `localFilePath` couvrent toute la bibliothèque.
  Réglages → « Measure the library now » appelle enfin `enqueueNow`, qui n'avait aucun appelant.
- **Cadenas par message** dans les conversations, en plus de la barre de séparation E2EE.

## Reste à faire

### À tester à la main

Tout ce qui suit ne peut pas être couvert par un test unitaire : il faut un
appareil, des fichiers réels, ou deux téléphones. C'est la liste complète de ce
qui reste à vérifier, du plus risqué au moins risqué. Le reste du dépôt est
vert : 206 tests côté Wanda, 378 côté Agro.

1. **#35 — relais E2EE, sur deux appareils réels.** Le plus risqué de la liste.
   La couture Media3 n'est exercée par aucun test : `RelayDecryptingDataSource`
   n'est appelé nulle part en test et aucune lecture n'a jamais été lancée. Les
   points qui mordront : le chemin des en-têtes, l'envoi sans `Content-Length`,
   et la fin de flux. Un échec ici est silencieux — la lecture s'arrête, sans
   erreur.

2. **PR #41 — les mesures acoustiques, sur une vraie bibliothèque.** Toute
   l'arithmétique est testée, la *mesure* ne l'est pas. Ce qu'il faut vérifier :
   qu'un morceau à 130 BPM ressort bien près de 130 (`AcousticFeatures.bpmOf`
   sur la ligne stockée), qu'un morceau calme et un morceau compressé ne
   tombent pas sur la même énergie, et qu'une radio lancée deux fois de suite
   ne redonne pas la même file. Lancer l'indexation depuis Réglages
   (`FingerprintIndexWorker.enqueueNow`), puis une radio.

3. **PR #41 — la part de découverte tient.** Sur une bibliothèque entièrement
   indexée, une radio doit **toujours** contenir des morceaux jamais mesurés.
   Si elle ne rejoue que la bibliothèque, la garantie a sauté : c'est le mode
   d'échec que tout le design évite, et il ressemble à un fonctionnement
   normal.

4. **PR #40 — le bug « Memories ».** Deux morceaux du même titre par des
   artistes différents, l'un téléchargé et l'autre pas. Le second ne doit plus
   jouer l'audio du premier. Vérifier aussi qu'un morceau *légitimement*
   partagé entre deux sources se substitue toujours — la correction ne doit pas
   avoir cassé le repli hors-ligne.

5. **PR #25 (Agro) — les vecteurs traversent.** Deux appareils appairés au même
   serveur : le second doit voir les mesures du premier via
   `similarRecordings`. Un seed jamais mesuré doit renvoyer **vide**, pas les
   morceaux les plus connus.

6. **PR #37 — les liens d'empreinte fusionnent bien.** Deux copies d'un même
   morceau avec des tags délibérément différents (l'une titrée
   `Song (Official Video) [HQ]`, l'artiste étant le nom de la chaîne). Après
   indexation, les deux lignes doivent se replier en une seule dans la
   bibliothèque **et** dans l'aperçu de fusion — et « ce n'est pas le même
   enregistrement » doit toujours les séparer ensuite.

7. **PR #38 — l'étagère « Popular on Agro ».** Elle n'apparaît qu'au-dessus du
   plancher d'exposition (5 écoutes). Sur un serveur neuf, elle est vide et
   c'est correct : vérifier qu'elle est *absente* et non vide-mais-affichée.
   La contribution est opt-in, par défaut désactivée — vérifier que rien ne
   part avant que le réglage soit activé.

8. **PR #42 — fredonner un air.** Le seul point où la mesure est déjà
   partiellement vérifiée : les tests couvrent YIN sur des tons de synthèse et
   un triolet do-mi-sol ressort bien en `0, +4, +3`. Ce qui reste à voir sur
   appareil, c'est l'inverse — les contours **stockés**, extraits de vrais
   mixages. Le suivi de hauteur suppose une voix à la fois : une guitare-voix
   ou un piano seul donneront un bon contour, un mur de production donnera
   souvent la basse au lieu de la mélodie. Fredonner un air de chaque type et
   voir lequel répond. Un échec attendu ici, pas un bug.

0. **L'off-grid ne se connecte toujours pas. C'est le point ouvert numéro un.**

   La découverte marche : les deux téléphones se voient en BLE, la liste se remplit, l'empreinte
   s'affiche. C'est la formation du groupe Wi-Fi Direct qui échoue, et `dumpsys wifip2p` est le
   seul outil qui dise où — la ligne `CONNECT` et son `dest=` :

   | trace | signification |
   |---|---|
   | `dest=<null>` en `InactiveState` | refus immédiat : le pair n'est plus dans le cache |
   | `dest=<null>` en `GroupCreatedState` | ce téléphone possède déjà un groupe |
   | `dest=ProvisionDiscoveryState` | ça avance, on attend le dialogue système |
   | reste en `GroupNegotiationState` | la négociation ne conclut pas |

   Quatre versions ont été essayées en une soirée, et **deux ont cassé la précédente** :

   - `createGroup()` d'origine : chaque appareil formait un groupe d'un seul membre et
     `groupOwnerAddress` était sa **propre** adresse. Il s'appariait avec lui-même — attrapé par la
     vérification d'empreinte, qui a donc fait son travail.
   - `stopPeerDiscovery` avant `connect` : vide le cache de pairs du framework, donc `connect`
     nomme un appareil qu'il ne connaît plus. Refus instantané.
   - `hostGroup()` sur le chemin « be findable » : comme les deux appareils doivent être trouvables
     pour se voir, les deux devenaient propriétaires, et un propriétaire ne peut pas rejoindre le
     groupe d'un autre. Défaut de conception, pas de plomberie.
   - **État actuel**, non validé : découverte seule côté trouvable, négociation normale au tap avec
     `groupOwnerIntent = 0` (le tapeur devient client, car le framework ne rend que l'adresse du
     *propriétaire*), délais séparés (12 s de scan, 75 s de négociation), garde contre les scans
     parasites pendant la négociation, et abandon d'un éventuel groupe résiduel avant de connecter.

   Cette combinaison n'a jamais tourné ensemble. La fois où la négociation a été atteinte, elle a
   été tuée à 30 s ; il est possible qu'elle aboutissait. À reprendre **depuis la trace**, pas
   depuis une hypothèse sur ce que l'API devrait faire : c'est ce qui a coûté les trois régressions
   ci-dessus.

   Non résolu par ailleurs : `« Relay stream is not an encrypted Wanda stream »`, vu une fois sur
   une vidéo. L'en-tête `x-agro-sealed-key` était présent mais le corps ne portait pas le cadrage.

9. **PR #43 — le lien hors-réseau.** Rien de tout cela ne tourne sur la JVM :
   BLE et Wi-Fi Direct sont du framework. Deux téléphones, Wi-Fi et données
   coupées sur les deux : l'un partage, l'autre doit le voir apparaître en
   quelques secondes, puis lire un morceau. À noter, ce ne sont pas des bugs :
   la formation du groupe affiche une boîte de dialogue système sur la plupart
   des appareils, certains constructeurs échouent en silence, et un téléphone
   déjà en 2,4 GHz négociera parfois le groupe là et donnera une fraction du
   débit annoncé.

10. **PR #43 — le chiffrement LAN.** Vérifier qu'un morceau lu depuis un pair
    sur le réseau local porte bien le cadenas dans le badge, et qu'une capture
    réseau ne montre plus l'audio en clair. C'était le trou : le relais
    lointain était chiffré, le lien local ne l'était pas.

11. **PR #44 — le lien d'album universel.** Le seul qui demande deux personnes
    aux sources différentes : partager un album depuis une bibliothèque locale
    et l'ouvrir sur un appareil qui n'a que YouTube Music, ou l'inverse. Un
    album introuvable doit dire lequel et chez qui, pas échouer en silence.
    Vérifier aussi qu'un appui long sur une pochette ouvre bien la feuille —
    `combinedClickable` sur une carte dans une grille est le genre de geste qui
    entre en conflit avec le défilement.
11. **Le catalogue serveur est vide.** 13 fichiers sur 13 sont empreints sur l'appareil
   (229 672 landmarks, 13 vecteurs), mais `catalog_recordings` est à **0** sur Agro : la
   publication n'a jamais tourné. Ce sont deux workers distincts et il faut les deux, dans cet
   ordre — Réglages → « Measure the library now » **calcule**, puis « Sync now »
   (`LibrarySyncWorker`) **publie**. À revérifier maintenant que les sources streamées sont
   indexables.

12. **Migration 21 → 22, puis 22 → 23.** Installer par-dessus une base existante et vérifier
   que rien n'est perdu. La table `track_features` démarre vide par
   conception ; les vecteurs arrivent à la prochaine indexation.

### Vague 5 — découverte

Les deux premiers points sont livrés et en attente de relecture : Wanda #41 et
Agro #25 (vecteurs acoustiques, PR ouvertes), Wanda #38 et Agro #24 (compteurs,
fusionnées). Reste :

9. **Wanda #33 + #29 fusionnées** — reconnaissance extérieure. Il reste le moteur
   B : suivi de hauteur YIN, entité de contour mélodique, appariement DTW, puis
   refonte de `RecognitionRepository` pour que les deux moteurs consomment une
   seule capture micro et alimentent une seule liste classée.

### Vague 6 — les gros morceaux
10. **Wanda #32** — transport hors-réseau Wi-Fi Direct / BLE / LocalOnlyHotspot.
   Le transport, l'écran et la poignée de main X25519 existent désormais ; il reste la formation du
   groupe Wi-Fi Direct, voir le point 0 de la liste à tester. À construire derrière l'abstraction `ResolvedFrom`
   existante pour que `ListenAlongResolver` gagne un palier au lieu d'être réécrit.
   À découper en épopée : découverte BLE + poignée de main X25519, montée en
   Wi-Fi Direct, pair PC.
11. **Wanda #25** — la feuille d'actions album est petite, mais les liens
   universels inter-sources sont une vraie fonctionnalité. `ShareKind.ALBUM`
   existe déjà ; le blocage est que `AlbumCard` n'a pas d'appui long, et qu'il
   n'existe aucun lien « Wanda » agnostique — `ShareRepository` ne fait que
   relayer le lien forgé par chaque backend.

### Indépendant
12. **Agro #13** — CrowdSec tourne **devant** Agro, pas dedans. Deux parties :
   donner aux vérifications 2FA leur propre seau de limitation dans `login.rs`
   avec des codes HTTP distincts, puis fournir la configuration de parseur
   CrowdSec en artefact de déploiement. À noter : `audit.rs` tronque les IP en
   /24-/64 par conception, donc c'est le proxy et non Agro qui décide par IP.
   L'issue mentionne aussi WebAuthn/FIDO2 : Agro n'a que TOTP, rien pour WebAuthn.

---

## Le bug « Memories » — résolu

Cause trouvée, corrigée dans la PR #40 (issue #39). Le diagnostic noté ici était
juste sur un point et faux sur l'autre : `TrackDeduplicator` n'était pas en
cause, parce que le chemin de résolution **ne l'appelait pas du tout**.

`MusicRepository.getStreamInfo()` demandait à Room une seule ligne avec
`title = :title COLLATE NOCASE LIMIT 1`, deux fois — une copie locale ou
téléchargée, puis une copie Navidrome. Ni l'artiste, ni la durée, ni les
scissions, ni les liens d'empreinte n'étaient consultés. Le troisième palier
avait le même trou par un autre chemin : le repli par recherche Navidrome
comparait les titres via un helper qui minuscule et retire la ponctuation.

Les requêtes rendent désormais des *candidats* et `selectSameRecording` tranche
avec le même test d'identité que la bibliothèque et l'aperçu de fusion.
Conséquence assumée : un candidat sans durée ne se substitue plus, sauf si une
empreinte le vouche.

Côté Agro il n'y a **rien à réinitialiser** : `library_tracks` a `content_hash` en
clé primaire, donc deux fichiers différents sont toujours deux lignes ; les colonnes
`norm_*` ne servent qu'aux requêtes de correspondance.

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
