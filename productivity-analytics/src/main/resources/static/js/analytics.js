(() => {
  const start = document.getElementById('analytics-from');
  const end = document.getElementById('analytics-to');
  const syncMinimumEnd = () => { end.min = start.value; };

  start.addEventListener('input', syncMinimumEnd);
  syncMinimumEnd();
})();
