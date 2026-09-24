package com.calcul

import scala.scalajs.js

object MockDom:

  def install(): Unit =
    js.eval(
      """
        if (typeof globalThis.document === 'undefined') {
          const doc = {
            createElement: function(tag) {
              const children = [];
              const attrs = {};
              return {
                tagName: tag.toUpperCase(),
                nodeType: 1,
                style: {},
                classList: { add: function(){}, remove: function(){}, contains: function(){ return false; } },
                setAttribute: function(k, v){ attrs[k] = v; },
                getAttribute: function(k){ return attrs[k] || null; },
                hasAttribute: function(k){ return k in attrs; },
                removeAttribute: function(k){ delete attrs[k]; },
                setAttributeNS: function(ns, k, v){ attrs[k] = v; },
                getAttributeNS: function(ns, k){ return attrs[k] || null; },
                hasAttributeNS: function(ns, k){ return k in attrs; },
                removeAttributeNS: function(ns, k){ delete attrs[k]; },
                appendChild: function(c){ children.push(c); return c; },
                removeChild: function(c){ return c; },
                addEventListener: function(){},
                removeEventListener: function(){},
                children: children,
                childNodes: children
              };
            },
            createTextNode: function(t){ return { nodeType: 3, textContent: t, data: t }; },
            createComment: function(t){ return { nodeType: 8, data: t }; },
            createDocumentFragment: function(){ return { nodeType: 11, appendChild: function(c){ return c; } }; }
          };
          doc.createElementNS = function(ns, tag){ return doc.createElement(tag); };
          globalThis.document = doc;
          global.document = doc;
          if (typeof globalThis.window === 'undefined') {
            globalThis.window = { document: doc, location: { href: '' } };
            global.window = globalThis.window;
          }
        }
      """
    )
