(function (engine, number) {
  try {
    var title = document.title || '';
    var host = location.hostname || '';
    if (/安全验证|访问验证|验证码|搜狗搜索访问验证/.test(title) ||
        /wappass\.baidu|passport\.baidu|antispider|captcha/.test(host) ||
        document.querySelector('#verify, .vcode-spin, #seccodeInput, form[action*="captcha"]')) {
      return JSON.stringify({status: 'verify', results: []});
    }
    var results = [];
    function text(node) {
      if (!node) return '';
      var clean = node.cloneNode(true);
      var hidden = clean.querySelectorAll('script, style, noscript, template');
      for (var n = 0; n < hidden.length; n++) hidden[n].remove();
      return (clean.textContent || '').replace(/\s+/g, ' ').trim();
    }
    function matchesNumber(value) {
      var pattern = number.split('').join('[\\s\\-()]*');
      return new RegExp('(^|[^0-9])' + pattern + '(?![0-9])').test(value);
    }
    function matchesPageQuery() {
      try {
        var query = new URL(location.href).searchParams.get('keyword');
        var input = document.querySelector('input[name="keyword"], input#keyword');
        // Service cards may list central hotlines rather than the searched regional number.
        // Associate them only with this exact search response, never by matching number suffixes.
        return query === number && (!input || input.value.trim() === number);
      } catch (ignored) { return false; }
    }
    function add(titleText, snippet, url) {
      if (!snippet || !titleText) return;
      var key = snippet.replace(/[\s，。,、;；:：·|]/g, '');
      if (results.some(function (item) { return item.key === key; })) return;
      results.push({kind: 'number_service', title: titleText.slice(0, 180), snippet: snippet.slice(0, 420), url: url, key: key});
    }
    // Strict allowlist: only known number-query/service modules. Never read organic or AI cards.
    if (engine === 'baidu') {
      var baiduCards = document.querySelectorAll('[tpl="ms_num_mark"], [srcid="ms_num_mark"]');
      for (var b = 0; b < baiduCards.length; b++) {
        var baiduCard = baiduCards[b];
        if (baiduCard.closest('#results-baikan, .se-results-baikan, [tpl="new_baikan_index"], [srcid="new_baikan_index"]')) continue;
        if (!matchesNumber(text(baiduCard.querySelector('h3, .cosc-title, .result_title')))) continue;
        var locationText = text(baiduCard.querySelector('[class*="loc-msg"], .location-info'));
        var markText = text(baiduCard.querySelector('[class*="mark-tag"]'));
        add(number + '号码查询', [locationText, markText].filter(Boolean).join(' '), location.href);
      }
    }
    if (engine === 'qihoo') {
      var phoneCard = document.querySelector('#mohe-m-mobilecheck');
      if (phoneCard && matchesNumber(text(phoneCard.querySelector('.mh-tel-num')))) {
        var phoneDetails = [text(phoneCard.querySelector('.mh-tel-adr'))];
        var phoneMark = text(phoneCard.querySelector('.mh-tel-mark'));
        if (phoneMark) phoneDetails.push(phoneMark);
        add(number + '号码查询', phoneDetails.join(' '), location.href);
      }
      var serviceCard = document.querySelector('#mohe-m-biu_kefudianhua');
      if (serviceCard) {
        var services = serviceCard.querySelectorAll('[data-tel]');
        for (var k = 0; k < services.length; k++) {
          if (!matchesNumber(services[k].getAttribute('data-tel') || '')) continue;
          var serviceName = text(serviceCard.querySelector('h3')).replace(/[_＿]客服电话$/, '');
          add(number + '号码查询', serviceName + ' ' + text(services[k].querySelector('.mh-first-line')), location.href);
        }
      }
    }
    if (engine === 'sogou') {
      var numberCards = document.querySelectorAll('[class*="number-query"]');
      for (var p = 0; p < numberCards.length; p++) {
        var numberCard = numberCards[p];
        var infos = numberCard.querySelectorAll('.singleTel .text-layout');
        if (!infos.length) {
          var serviceHeading = numberCard.querySelector('.major-title .resultLink');
          var serviceTitle = text(serviceHeading);
          if (!matchesPageQuery() || !/[-－—_＿]\s*号码查询服务$/.test(serviceTitle) ||
              !numberCard.querySelector('.content-box .list-img-flex .imgtext-column')) continue;
          var serviceName = serviceTitle.replace(/\s*[-－—_＿]\s*号码查询服务$/, '').trim();
          var serviceLabels = numberCard.querySelectorAll('.list-img-flex .imgtext-column .text-layout > p.text-gray');
          var servicePieces = serviceName ? [serviceName] : [];
          for (var t = 0; t < serviceLabels.length; t++) {
            var serviceLabel = text(serviceLabels[t]);
            if (serviceLabel && servicePieces.indexOf(serviceLabel) < 0) servicePieces.push(serviceLabel);
          }
          add(number + '号码查询', servicePieces.join(' '), location.href);
          continue;
        }
        for (var u = 0; u < infos.length; u++) {
          var info = infos[u];
          if (!matchesNumber(text(info))) continue;
          var spans = info.querySelectorAll('p.fz-big span');
          var pieces = [];
          for (var s = 0; s < spans.length; s++) {
            var value = text(spans[s]);
            if (s === 0) value = value.replace(/中国$/, '');
            if (value) pieces.push(value);
          }
          var labels = info.querySelectorAll('p:not(.fz-big) span');
          for (var l = 0; l < labels.length; l++) {
            var label = text(labels[l]);
            if (label && !/^[+\d\s()\-]+$/.test(label)) pieces.push(label);
          }
          add(number + '号码查询', pieces.join(' '), location.href);
        }
      }
    }
    if (results.length) return JSON.stringify({status: 'ok', results: results});
    var body = text(document.body);
    if (/没有找到|未找到相关|暂无相关|抱歉.{0,20}没有|没有更多结果/.test(body)) {
      return JSON.stringify({status: 'empty', results: []});
    }
    return JSON.stringify({status: 'pending', results: []});
  } catch (error) {
    return JSON.stringify({status: 'pending', results: []});
  }
})(__ENGINE__, __NUMBER__);
