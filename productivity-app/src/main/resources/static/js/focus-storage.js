(() => {
  const url = document.querySelector('[data-storage-materials]')?.dataset.storageMaterials;
  if (!url) return;

  let checking = false;

  async function reconcileMaterials() {
    if (document.hidden || checking) return;
    const files = document.querySelectorAll('[data-storage-file]');
    const links = document.querySelectorAll('[data-storage-link]');
    if (!files.length && !links.length) return;

    checking = true;
    try {
      const response = await fetch(url, {cache: 'no-store'});
      if (response.status !== 200) return;
      const materials = await response.json();
      const visibleFiles = new Set(materials.files.map(String));
      const visibleLinks = new Set(materials.links.map(String));
      files.forEach(row => {
        if (!visibleFiles.has(row.dataset.storageFile)) row.remove();
      });
      links.forEach(row => {
        if (!visibleLinks.has(row.dataset.storageLink)) row.remove();
      });
    } catch {
      // Keep the current view if the local service is unavailable.
    } finally {
      checking = false;
    }
  }

  document.addEventListener('visibilitychange', reconcileMaterials);
  window.addEventListener('focus', reconcileMaterials);
  window.setInterval(reconcileMaterials, 5000);
})();
