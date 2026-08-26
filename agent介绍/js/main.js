(function () {
  var tabButtons = Array.prototype.slice.call(document.querySelectorAll('.tab-btn'));
  var sections = Array.prototype.slice.call(document.querySelectorAll('.chapter'));
  if (!tabButtons.length || !sections.length) return;

  function activate(id, scrollToTop) {
    tabButtons.forEach(function (btn) {
      btn.classList.toggle('active', btn.dataset.target === id);
    });
    sections.forEach(function (sec) {
      sec.classList.toggle('active', sec.id === id);
    });
    if (scrollToTop) window.scrollTo(0, 0);
    if (location.hash !== '#' + id) history.replaceState(null, '', '#' + id);
  }

  var tabsBar = document.querySelector('.tabs');
  if (tabsBar) {
    tabsBar.addEventListener('click', function (e) {
      var btn = e.target.closest('.tab-btn');
      if (!btn) return;
      activate(btn.dataset.target, true);
    });
  }

  window.addEventListener('hashchange', function () {
    var id = location.hash.slice(1);
    if (document.getElementById(id)) activate(id, false);
  });

  var initial = location.hash.slice(1);
  if (initial && document.getElementById(initial)) {
    activate(initial, false);
  } else {
    activate(sections[0].id, false);
  }
})();
