# frwd.top — the share-link page

Static pages served by GitHub Pages from this folder on `main`. No build step, no workflow: what
is here is what is served.

| File | Served at | Purpose |
|---|---|---|
| `listen/index.html` | `frwd.top/listen` | Forwards a shared link to the track it points at |
| `index.html` | `frwd.top` | So the bare domain is not a 404 |
| `CNAME` | — | Tells Pages which domain to answer on |
| `.nojekyll` | — | Serves the files as-is instead of running them through Jekyll |

## Enabling it

1. **Repo → Settings → Pages** → Source: *Deploy from a branch*, Branch: `main`, folder: `/docs`.
   (Pages on a private repo needs a paid plan; this repo being public is what makes it free.)
2. **DNS at the registrar**, apex `frwd.top`:

   ```
   A     frwd.top   185.199.108.153
   A     frwd.top   185.199.109.153
   A     frwd.top   185.199.110.153
   A     frwd.top   185.199.111.153
   AAAA  frwd.top   2606:50c0:8000::153
   AAAA  frwd.top   2606:50c0:8001::153
   AAAA  frwd.top   2606:50c0:8002::153
   AAAA  frwd.top   2606:50c0:8003::153
   ```

3. Back in **Settings → Pages**, tick **Enforce HTTPS** once the certificate is issued (minutes to
   an hour after DNS resolves).
4. In Wanda: **Settings → Sharing → Custom share domain** → `frwd.top`.

## Adding a music host

`ALLOWED_HOSTS` in `listen/index.html` is the whole security model: the page forwards to those
hosts and refuses everything else. It currently holds YouTube's hosts and `music.kolbxyz.xyz`.
Add a host only when it is one Wanda itself mints links for — a redirector that forwards anywhere
is an open redirect wearing this domain.
