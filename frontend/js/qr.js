/**
 * SmartOPD – Standalone Pure JavaScript QR Code Generator
 * File: js/qr.js
 * 
 * ============================================================================
 * Self-contained QR Code Generator (Zero external dependencies / Zero CDNs)
 * Generates standards-compliant QR Code Matrix (Model 2, Byte Encoding)
 * and renders crisp, scalable inline SVG strings or renders into DOM elements.
 * ============================================================================
 * 
 * Intended for:
 * - Patient OPD Token Slips (quick scan via smartphone camera)
 * - Waiting room display signs
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof exports === 'object') {
    module.exports = factory();
  } else {
    root.SmartOPDQR = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {

  // --- Galois Field GF(256) Math & Polynomials for Reed-Solomon ECC ---
  const EXP_TABLE = new Uint8Array(256);
  const LOG_TABLE = new Uint8Array(256);

  (function initGaloisField() {
    let x = 1;
    for (let i = 0; i < 255; i++) {
      EXP_TABLE[i] = x;
      LOG_TABLE[x] = i;
      x <<= 1;
      if (x & 256) x ^= 0x11d; // Primitive polynomial x^8 + x^4 + x^3 + x^2 + 1
    }
    LOG_TABLE[0] = 0;
  })();

  function gmult(a, b) {
    if (a === 0 || b === 0) return 0;
    return EXP_TABLE[(LOG_TABLE[a] + LOG_TABLE[b]) % 255];
  }

  function rsGenPoly(degree) {
    let poly = [1];
    for (let i = 0; i < degree; i++) {
      const next = [1];
      const factor = EXP_TABLE[i];
      for (let j = 0; j < poly.length; j++) {
        next.push(0);
      }
      for (let j = 0; j < poly.length; j++) {
        next[j + 1] ^= gmult(poly[j], factor);
      }
      poly = next;
    }
    return poly;
  }

  function rsCalculateECC(data, eccLength) {
    const gen = rsGenPoly(eccLength);
    const msg = new Uint8Array(data.length + eccLength);
    msg.set(data);

    for (let i = 0; i < data.length; i++) {
      const coef = msg[i];
      if (coef !== 0) {
        for (let j = 0; j < gen.length; j++) {
          msg[i + j] ^= gmult(gen[j], coef);
        }
      }
    }
    return msg.slice(data.length);
  }

  // --- QR Code Version Table (Capacities and Blocks for ECC Level M) ---
  // [Version, TotalCodewords, ECCPerBlock, BlocksGroup1, DataPerBlock1, BlocksGroup2, DataPerBlock2]
  const VERSION_SPECS_M = [
    null,
    [1, 26, 10, 1, 16, 0, 0],   // V1: 21x21, 16 data bytes
    [2, 44, 16, 1, 28, 0, 0],   // V2: 25x25, 28 data bytes
    [3, 70, 26, 1, 44, 0, 0],   // V3: 29x29, 44 data bytes
    [4, 100, 18, 2, 32, 0, 0],  // V4: 33x33, 64 data bytes
    [5, 134, 24, 2, 43, 0, 0],  // V5: 37x37, 86 data bytes
    [6, 172, 16, 4, 27, 0, 0],  // V6: 41x41, 108 data bytes
    [7, 196, 18, 4, 31, 0, 0]   // V7: 45x45, 124 data bytes
  ];

  // Alignment Pattern Centers per Version
  const ALIGNMENT_PATTERNS = [
    [],
    [],
    [6, 18],
    [6, 22],
    [6, 26],
    [6, 30],
    [6, 34],
    [6, 22, 38]
  ];

  /**
   * Encodes a string into UTF-8 bytes
   */
  function toUTF8Bytes(str) {
    const bytes = [];
    for (let i = 0; i < str.length; i++) {
      let code = str.charCodeAt(i);
      if (code < 0x80) {
        bytes.push(code);
      } else if (code < 0x800) {
        bytes.push(0xc0 | (code >> 6));
        bytes.push(0x80 | (code & 0x3f));
      } else {
        bytes.push(0xe0 | (code >> 12));
        bytes.push(0x80 | ((code >> 6) & 0x3f));
        bytes.push(0x80 | (code & 0x3f));
      }
    }
    return bytes;
  }

  /**
   * Finds smallest version supporting the data length with ECC Level M
   */
  function pickVersion(dataByteLength) {
    for (let v = 1; v < VERSION_SPECS_M.length; v++) {
      const spec = VERSION_SPECS_M[v];
      const maxData = (spec[3] * spec[4]) + (spec[5] * spec[6]);
      // Byte mode header: 4 bits mode + 8 bits length (for V1-V9)
      const overhead = 2; // 12 bits -> 2 bytes approx
      if (dataByteLength + overhead <= maxData) {
        return v;
      }
    }
    return 7; // Fallback to max supported version
  }

  /**
   * BitBuffer helper class to pack bits into bytes
   */
  class BitBuffer {
    constructor() {
      this.buffer = [];
      this.length = 0;
    }
    put(val, bits) {
      for (let i = 0; i < bits; i++) {
        this.putBit(((val >>> (bits - i - 1)) & 1) === 1);
      }
    }
    putBit(bit) {
      const byteIndex = Math.floor(this.length / 8);
      if (this.buffer.length <= byteIndex) {
        this.buffer.push(0);
      }
      if (bit) {
        this.buffer[byteIndex] |= (0x80 >>> (this.length % 8));
      }
      this.length++;
    }
  }

  /**
   * Core QR Matrix Builder
   */
  function createQRMatrix(text) {
    const dataBytes = toUTF8Bytes(text);
    const version = pickVersion(dataBytes.length);
    const spec = VERSION_SPECS_M[version];
    const totalDataCapacity = (spec[3] * spec[4]) + (spec[5] * spec[6]);

    // 1. Pack Byte Mode Data
    const bb = new BitBuffer();
    bb.put(0x4, 4); // Byte Mode Indicator
    bb.put(dataBytes.length, 8); // Character count (8 bits for V1-V9)
    for (let i = 0; i < dataBytes.length; i++) {
      bb.put(dataBytes[i], 8);
    }

    // 2. Terminator & Padding
    const remainingBits = (totalDataCapacity * 8) - bb.length;
    if (remainingBits >= 4) {
      bb.put(0, 4);
    } else if (remainingBits > 0) {
      bb.put(0, remainingBits);
    }
    while (bb.length % 8 !== 0) {
      bb.putBit(false);
    }

    const padBytes = [0xec, 0x11];
    let padIdx = 0;
    while (bb.buffer.length < totalDataCapacity) {
      bb.buffer.push(padBytes[padIdx % 2]);
      padIdx++;
    }

    // 3. Block Partitioning & Reed-Solomon ECC Calculation
    const eccPerBlock = spec[2];
    const numBlocksG1 = spec[3];
    const dataPerBlockG1 = spec[4];
    const numBlocksG2 = spec[5];
    const dataPerBlockG2 = spec[6];
    const totalBlocks = numBlocksG1 + numBlocksG2;

    const dataBlocks = [];
    const eccBlocks = [];
    let offset = 0;

    for (let i = 0; i < totalBlocks; i++) {
      const blockSize = i < numBlocksG1 ? dataPerBlockG1 : dataPerBlockG2;
      const blockData = bb.buffer.slice(offset, offset + blockSize);
      offset += blockSize;
      dataBlocks.push(blockData);
      eccBlocks.push(rsCalculateECC(blockData, eccPerBlock));
    }

    // 4. Interleaving Data and ECC Codewords
    const finalCodewords = [];
    let maxDataLen = Math.max(dataPerBlockG1, dataPerBlockG2);
    for (let i = 0; i < maxDataLen; i++) {
      for (let b = 0; b < totalBlocks; b++) {
        if (i < dataBlocks[b].length) {
          finalCodewords.push(dataBlocks[b][i]);
        }
      }
    }
    for (let i = 0; i < eccPerBlock; i++) {
      for (let b = 0; b < totalBlocks; b++) {
        finalCodewords.push(eccBlocks[b][i]);
      }
    }

    // 5. Initialize Module Grid
    const moduleCount = version * 4 + 17;
    const matrix = [];
    const isReserved = [];
    for (let r = 0; r < moduleCount; r++) {
      matrix.push(new Array(moduleCount).fill(false));
      isReserved.push(new Array(moduleCount).fill(false));
    }

    // 6. Place Finder Patterns (Top-Left, Top-Right, Bottom-Left)
    function placeFinder(row, col) {
      for (let r = -1; r <= 7; r++) {
        for (let c = -1; c <= 7; c++) {
          const mr = row + r;
          const mc = col + c;
          if (mr >= 0 && mr < moduleCount && mc >= 0 && mc < moduleCount) {
            isReserved[mr][mc] = true;
            if (r >= 0 && r <= 6 && c >= 0 && c <= 6) {
              const isBlack = (r === 0 || r === 6 || c === 0 || c === 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4));
              matrix[mr][mc] = isBlack;
            } else {
              matrix[mr][mc] = false; // Separator
            }
          }
        }
      }
    }

    placeFinder(0, 0);
    placeFinder(0, moduleCount - 7);
    placeFinder(moduleCount - 7, 0);

    // 7. Place Alignment Patterns
    const alignCoords = ALIGNMENT_PATTERNS[version] || [];
    for (let i = 0; i < alignCoords.length; i++) {
      for (let j = 0; j < alignCoords.length; j++) {
        const ar = alignCoords[i];
        const ac = alignCoords[j];
        if (isReserved[ar][ac]) continue;

        for (let r = -2; r <= 2; r++) {
          for (let c = -2; c <= 2; c++) {
            isReserved[ar + r][ac + c] = true;
            matrix[ar + r][ac + c] = (Math.max(Math.abs(r), Math.abs(c)) !== 1);
          }
        }
      }
    }

    // 8. Place Timing Patterns
    for (let i = 8; i < moduleCount - 8; i++) {
      if (!isReserved[6][i]) {
        matrix[6][i] = (i % 2 === 0);
        isReserved[6][i] = true;
      }
      if (!isReserved[i][6]) {
        matrix[i][6] = (i % 2 === 0);
        isReserved[i][6] = true;
      }
    }

    // 9. Dark Module & Reserve Format Areas
    matrix[moduleCount - 8][8] = true;
    isReserved[moduleCount - 8][8] = true;

    for (let i = 0; i < 9; i++) {
      if (i !== 6) { isReserved[8][i] = true; isReserved[i][8] = true; }
    }
    for (let i = moduleCount - 8; i < moduleCount; i++) {
      isReserved[8][i] = true;
      isReserved[i][8] = true;
    }

    // 10. Place Data Bits with Standard Mask Pattern 0: (row + col) % 2 === 0
    let bitIndex = 0;
    const totalBits = finalCodewords.length * 8;
    let up = true;

    for (let right = moduleCount - 1; right > 0; right -= 2) {
      if (right === 6) right--; // Skip vertical timing pattern column
      for (let vert = 0; vert < moduleCount; vert++) {
        const row = up ? (moduleCount - 1 - vert) : vert;
        for (let col = right; col > right - 2; col--) {
          if (!isReserved[row][col]) {
            let bit = false;
            if (bitIndex < totalBits) {
              const byteVal = finalCodewords[Math.floor(bitIndex / 8)];
              bit = ((byteVal >>> (7 - (bitIndex % 8))) & 1) === 1;
              bitIndex++;
            }
            // Invert bit if mask matches ((row + col) % 2 === 0)
            if ((row + col) % 2 === 0) {
              bit = !bit;
            }
            matrix[row][col] = bit;
          }
        }
      }
      up = !up;
    }

    // 11. Format Information Bits (ECC Level M = 00, Mask 000 = 00 -> 101010000010010 with XOR mask)
    const formatBits = [1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0];

    // Around Top-Left finder
    const fmtCoordsTL = [
      [8, 0], [8, 1], [8, 2], [8, 3], [8, 4], [8, 5],
      [8, 7], [8, 8], [7, 8], [5, 8], [4, 8], [3, 8], [2, 8], [1, 8], [0, 8]
    ];
    for (let i = 0; i < 15; i++) {
      matrix[fmtCoordsTL[i][0]][fmtCoordsTL[i][1]] = (formatBits[i] === 1);
    }

    // Along Top-Right and Bottom-Left finders
    for (let i = 0; i < 7; i++) {
      matrix[moduleCount - 1 - i][8] = (formatBits[i] === 1);
    }
    for (let i = 0; i < 8; i++) {
      matrix[8][moduleCount - 8 + i] = (formatBits[7 + i] === 1);
    }

    return {
      size: moduleCount,
      isDark: (r, c) => matrix[r][c]
    };
  }

  // --- Public SmartOPDQR API ---
  return {
    /**
     * Generates a self-contained inline SVG string representing the QR code.
     * 
     * @param {string} text - The URL or token data to encode
     * @param {number} size - Desired pixel width & height (e.g. 64, 120, 200)
     * @param {string} color - Fill color of dark modules (default #0B2545 navy)
     * @param {string} bgColor - Background color (default #FFFFFF)
     * @returns {string} Clean SVG markup string
     */
    generateSVG(text, size = 64, color = '#0B2545', bgColor = '#FFFFFF') {
      try {
        const qr = createQRMatrix(text);
        const moduleCount = qr.size;
        const margin = 2; // 2-module quiet zone margin
        const total = moduleCount + (margin * 2);

        let pathD = '';
        for (let r = 0; r < moduleCount; r++) {
          for (let c = 0; c < moduleCount; c++) {
            if (qr.isDark(r, c)) {
              pathD += `M${c + margin},${r + margin}h1v1h-1z `;
            }
          }
        }

        let svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${total} ${total}" width="${size}" height="${size}" shape-rendering="crispEdges" aria-label="QR Code for ${text}">`;
        if (bgColor && bgColor !== 'transparent') {
          svg += `<rect width="${total}" height="${total}" fill="${bgColor}"/>`;
        }
        svg += `<path d="${pathD.trim()}" fill="${color}"/>`;
        svg += `</svg>`;
        return svg;
      } catch (err) {
        console.error('Error generating QR Code SVG:', err);
        // Fallback placeholder SVG
        return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="${color}" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><circle cx="15.5" cy="8.5" r="1.5"/><path d="M7 16h10"/></svg>`;
      }
    },

    /**
     * Injects QR code SVG directly into an HTML element.
     * 
     * @param {HTMLElement|string} target - DOM Element or ID selector
     * @param {string} text - The URL or token string
     * @param {number} size - Target size in pixels
     */
    render(target, text, size = 64) {
      const el = typeof target === 'string' ? document.getElementById(target) : target;
      if (!el) return;
      el.innerHTML = this.generateSVG(text, size);
    }
  };
}));
