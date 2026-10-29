package com.asta.cherryoffice.ui.viewer

/** Runs once after local slide HTML is laid out, using the phone's actual font metrics. */
internal val presentationTextFitScript = """
    (() => {
      document.querySelectorAll('[data-slide-text]').forEach(box => {
        const content = box.querySelector('[data-slide-content]');
        const fonts = Array.from(content.querySelectorAll('[data-fit-font]'));
        const style = getComputedStyle(box);
        const width = Math.max(1, box.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight));
        const height = Math.max(1, box.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom));
        const apply = scale => fonts.forEach(el => {
          el.style.fontSize = (Number(el.dataset.fitFont) * scale) + 'px';
        });
        const fits = () => content.scrollHeight <= height && content.scrollWidth <= width + 1;
        apply(1);
        let scale = 1;
        if (!fits()) {
          let low = 0, high = 1;
          for (let i = 0; i < 12; i++) {
            const mid = (low + high) / 2;
            apply(mid);
            if (fits()) low = mid; else high = mid;
          }
          scale = low * 0.98;
          apply(scale);
        }
        box.dataset.fitScale = String(scale);
      });
      return true;
    })();
""".trimIndent()
