/**
 * SmartOPD – Inline SVG Charting Library
 * File: js/charts.js
 * 
 * ============================================================================
 * Pure Vanilla JavaScript & SVG Chart Generator (Zero CDN dependencies)
 * Fully responsive via SVG viewBox, accessible with titles and ARIA labels.
 * Designed to use the SmartOPD Design System CSS tokens.
 * 
 * Exposes SmartOPDCharts:
 * 1. renderBarChart(containerId, options)
 * 2. renderGroupedBarChart(containerId, options)
 * 3. renderLineChart(containerId, options)
 * 4. renderDonutChart(containerId, options)
 * ============================================================================
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define([], factory);
  } else if (typeof exports === 'object') {
    module.exports = factory();
  } else {
    root.SmartOPDCharts = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {

  // Design system color constants
  const COLORS = {
    tealPrimary: '#0F766E',
    tealAccent: '#14B8A6',
    tealLight: 'rgba(15, 118, 110, 0.15)',
    navyDark: '#0B2545',
    navyMuted: '#64748B',
    gridLine: '#E2E8F0',
    borderLight: '#CBD5E1',
    emergencyRed: '#DC2626',
    amberWarning: '#F59E0B',
    blueScheduled: '#2563EB',
    violetFairness: '#7C3AED',
    sevColors: {
      1: '#16A34A', // Green (Non-urgent)
      2: '#2563EB', // Blue (Standard)
      3: '#F59E0B', // Amber (Urgent)
      4: '#EA580C', // Orange (Very Urgent)
      5: '#DC2626'  // Red (Resuscitation)
    }
  };

  function getElement(target) {
    if (typeof target === 'string') {
      return document.getElementById(target);
    }
    return target;
  }

  return {
    COLORS,

    /**
     * 1. Render Single Bar Chart
     * Suitable for Average Wait per Doctor, Severity Distribution, etc.
     */
    renderBarChart(containerId, options = {}) {
      const el = getElement(containerId);
      if (!el) return;

      const {
        data = [],
        xKey = 'label',
        yKey = 'value',
        height = 240,
        width = 500,
        color = COLORS.tealPrimary,
        getColor = null,
        yLabel = '',
        xLabel = '',
        emptyMessage = 'No data available'
      } = options;

      if (!data || data.length === 0) {
        el.innerHTML = `<div style="text-align:center; padding: 40px; color:${COLORS.navyMuted}; font-size: 0.875rem;">${emptyMessage}</div>`;
        return;
      }

      const padding = { top: 25, right: 20, bottom: 45, left: 45 };
      const chartWidth = width - padding.left - padding.right;
      const chartHeight = height - padding.top - padding.bottom;

      const maxVal = Math.max(...data.map(d => Number(d[yKey]) || 0), 1);
      const yTicks = 4;
      const tickStep = Math.ceil(maxVal / yTicks);
      const yMax = tickStep * yTicks;

      // Build grid & Y-axis labels
      let gridHTML = '';
      for (let i = 0; i <= yTicks; i++) {
        const val = i * tickStep;
        const y = padding.top + chartHeight - (val / yMax) * chartHeight;
        gridHTML += `
          <line x1="${padding.left}" y1="${y}" x2="${width - padding.right}" y2="${y}" stroke="${COLORS.gridLine}" stroke-dasharray="3,3" stroke-width="1"/>
          <text x="${padding.left - 8}" y="${y + 4}" font-size="10" fill="${COLORS.navyMuted}" text-anchor="end" font-family="system-ui, sans-serif">${val}</text>
        `;
      }

      // Build Bars & X-axis labels
      const barGroupWidth = chartWidth / data.length;
      const barWidth = Math.max(12, Math.min(36, barGroupWidth * 0.65));

      let barsHTML = '';
      data.forEach((d, idx) => {
        const val = Number(d[yKey]) || 0;
        const barH = (val / yMax) * chartHeight;
        const x = padding.left + (idx * barGroupWidth) + (barGroupWidth - barWidth) / 2;
        const y = padding.top + chartHeight - barH;
        const barColor = getColor ? getColor(d, idx) : (d.color || color);

        barsHTML += `
          <g class="chart-bar-group" role="graphics-symbol" aria-label="${d[xKey]}: ${val}">
            <rect x="${x}" y="${y}" width="${barWidth}" height="${Math.max(2, barH)}" rx="3" fill="${barColor}">
              <title>${d[xKey]}: ${val} ${yLabel}</title>
            </rect>
            ${val > 0 ? `<text x="${x + barWidth / 2}" y="${y - 5}" font-size="10" font-weight="700" fill="${COLORS.navyDark}" text-anchor="middle" font-family="system-ui, sans-serif">${val}</text>` : ''}
            <text x="${x + barWidth / 2}" y="${padding.top + chartHeight + 16}" font-size="10" font-weight="500" fill="${COLORS.navyMuted}" text-anchor="middle" font-family="system-ui, sans-serif">${d[xKey]}</text>
          </g>
        `;
      });

      // Axis lines & labels
      const axisHTML = `
        <line x1="${padding.left}" y1="${padding.top + chartHeight}" x2="${width - padding.right}" y2="${padding.top + chartHeight}" stroke="${COLORS.borderLight}" stroke-width="1.5"/>
        <line x1="${padding.left}" y1="${padding.top}" x2="${padding.left}" y2="${padding.top + chartHeight}" stroke="${COLORS.borderLight}" stroke-width="1.5"/>
        ${yLabel ? `<text x="${padding.left}" y="${padding.top - 10}" font-size="10" font-weight="600" fill="${COLORS.navyMuted}" text-anchor="start">${yLabel}</text>` : ''}
        ${xLabel ? `<text x="${width / 2}" y="${height - 6}" font-size="10" font-weight="600" fill="${COLORS.navyMuted}" text-anchor="middle">${xLabel}</text>` : ''}
      `;

      el.innerHTML = `
        <svg viewBox="0 0 ${width} ${height}" width="100%" height="${height}" style="overflow: visible; display: block;" role="img" aria-label="${options.title || 'Bar Chart'}">
          <title>${options.title || 'Bar Chart'}</title>
          <desc>${options.desc || 'Bar chart showing ' + (yLabel || 'values') + ' across categories'}</desc>
          ${gridHTML}
          ${axisHTML}
          ${barsHTML}
        </svg>
      `;
    },

    /**
     * 2. Render Grouped Bar Chart
     * Compares multiple policies (e.g. FCFS vs Strict Priority vs SmartOPD) across categories
     */
    renderGroupedBarChart(containerId, options = {}) {
      const el = getElement(containerId);
      if (!el) return;

      const {
        categories = [],
        series = [],
        height = 260,
        width = 620,
        yLabel = 'Wait Time (mins)'
      } = options;

      if (!categories.length || !series.length) {
        el.innerHTML = `<div style="text-align:center; padding: 40px; color:${COLORS.navyMuted};">No comparison data available</div>`;
        return;
      }

      const padding = { top: 38, right: 20, bottom: 50, left: 45 };
      const chartWidth = width - padding.left - padding.right;
      const chartHeight = height - padding.top - padding.bottom;

      // Compute global maximum value across all series
      let maxVal = 1;
      series.forEach(s => {
        (s.values || []).forEach(v => {
          if (v > maxVal) maxVal = v;
        });
      });

      const yTicks = 4;
      const tickStep = Math.ceil(maxVal / yTicks);
      const yMax = tickStep * yTicks;

      // Legend HTML
      let legendHTML = '<g transform="translate(' + (padding.left + 5) + ', 14)">';
      let legendOffset = 0;
      series.forEach(s => {
        legendHTML += `
          <g transform="translate(${legendOffset}, 0)">
            <rect width="10" height="10" rx="2" fill="${s.color}"/>
            <text x="14" y="9" font-size="11" font-weight="600" fill="${COLORS.navyDark}" font-family="system-ui, sans-serif">${s.name}</text>
          </g>
        `;
        legendOffset += (s.name.length * 8) + 32;
      });
      legendHTML += '</g>';

      // Grid Lines
      let gridHTML = '';
      for (let i = 0; i <= yTicks; i++) {
        const val = i * tickStep;
        const y = padding.top + chartHeight - (val / yMax) * chartHeight;
        gridHTML += `
          <line x1="${padding.left}" y1="${y}" x2="${width - padding.right}" y2="${y}" stroke="${COLORS.gridLine}" stroke-dasharray="3,3" stroke-width="1"/>
          <text x="${padding.left - 8}" y="${y + 4}" font-size="10" fill="${COLORS.navyMuted}" text-anchor="end">${val}</text>
        `;
      }

      // Groups of bars
      const categoryWidth = chartWidth / categories.length;
      const numSeries = series.length;
      const subBarWidth = Math.max(8, Math.min(22, (categoryWidth * 0.70) / numSeries));
      const groupInnerWidth = subBarWidth * numSeries;

      let groupsHTML = '';
      categories.forEach((catName, catIdx) => {
        const groupX = padding.left + (catIdx * categoryWidth) + (categoryWidth - groupInnerWidth) / 2;

        series.forEach((s, sIdx) => {
          const val = Number(s.values[catIdx]) || 0;
          const barH = (val / yMax) * chartHeight;
          const x = groupX + (sIdx * subBarWidth);
          const y = padding.top + chartHeight - barH;

          groupsHTML += `
            <g class="chart-grouped-bar" role="graphics-symbol" aria-label="${s.name} - ${catName}: ${val} ${yLabel}">
              <rect x="${x}" y="${y}" width="${subBarWidth - 2}" height="${Math.max(2, barH)}" rx="2" fill="${s.color}">
                <title>${s.name} - ${catName}: ${val} ${yLabel}</title>
              </rect>
              ${val > 0 ? `<text x="${x + (subBarWidth - 2) / 2}" y="${y - 4}" font-size="9" font-weight="700" fill="${COLORS.navyDark}" text-anchor="middle">${val}</text>` : ''}
            </g>
          `;
        });

        // Category Label
        groupsHTML += `
          <text x="${padding.left + (catIdx * categoryWidth) + categoryWidth / 2}" y="${padding.top + chartHeight + 18}" font-size="11" font-weight="600" fill="${COLORS.navyDark}" text-anchor="middle">${catName}</text>
        `;
      });

      // Axis lines
      const axisHTML = `
        <line x1="${padding.left}" y1="${padding.top + chartHeight}" x2="${width - padding.right}" y2="${padding.top + chartHeight}" stroke="${COLORS.borderLight}" stroke-width="1.5"/>
        <line x1="${padding.left}" y1="${padding.top}" x2="${padding.left}" y2="${padding.top + chartHeight}" stroke="${COLORS.borderLight}" stroke-width="1.5"/>
        <text x="${width - padding.right}" y="${padding.top - 8}" font-size="10" font-weight="600" fill="${COLORS.navyMuted}" text-anchor="end">${yLabel}</text>
      `;

      el.innerHTML = `
        <svg viewBox="0 0 ${width} ${height}" width="100%" height="${height}" style="overflow: visible; display: block;" role="img" aria-label="${options.title || 'Grouped Bar Chart'}">
          <title>${options.title || 'Grouped Bar Chart'}</title>
          <desc>${options.desc || 'Comparative grouped bar chart across policies and patient categories'}</desc>
          ${legendHTML}
          ${gridHTML}
          ${axisHTML}
          ${groupsHTML}
        </svg>
      `;
    },

    /**
     * 3. Render Line Chart
     * Suitable for Hourly Registrations or Arrival Sequence Wait Times
     */
    renderLineChart(containerId, options = {}) {
      const el = getElement(containerId);
      if (!el) return;

      const {
        data = [],
        xKey = 'x',
        yKey = 'y',
        height = 220,
        width = 540,
        strokeColor = COLORS.tealPrimary,
        fillGradient = true,
        yLabel = 'Patients',
        xLabel = '',
        emptyMessage = 'No trend data'
      } = options;

      if (!data || data.length === 0) {
        el.innerHTML = `<div style="text-align:center; padding: 40px; color:${COLORS.navyMuted};">${emptyMessage}</div>`;
        return;
      }

      const padding = { top: 25, right: 20, bottom: 40, left: 45 };
      const chartWidth = width - padding.left - padding.right;
      const chartHeight = height - padding.top - padding.bottom;

      const maxVal = Math.max(...data.map(d => Number(d[yKey]) || 0), 1);
      const yTicks = 4;
      const tickStep = Math.ceil(maxVal / yTicks);
      const yMax = tickStep * yTicks;

      // Grid
      let gridHTML = '';
      for (let i = 0; i <= yTicks; i++) {
        const val = i * tickStep;
        const y = padding.top + chartHeight - (val / yMax) * chartHeight;
        gridHTML += `
          <line x1="${padding.left}" y1="${y}" x2="${width - padding.right}" y2="${y}" stroke="${COLORS.gridLine}" stroke-dasharray="3,3" stroke-width="1"/>
          <text x="${padding.left - 8}" y="${y + 4}" font-size="10" fill="${COLORS.navyMuted}" text-anchor="end">${val}</text>
        `;
      }

      // Calculate Coordinates
      const stepX = chartWidth / Math.max(1, data.length - 1);
      const points = data.map((d, i) => {
        const val = Number(d[yKey]) || 0;
        const x = padding.left + (i * stepX);
        const y = padding.top + chartHeight - (val / yMax) * chartHeight;
        return { x, y, val, label: d[xKey] };
      });

      const polylinePoints = points.map(p => `${p.x},${p.y}`).join(' ');

      // Area path
      const areaPath = `
        M ${points[0].x},${padding.top + chartHeight}
        L ${polylinePoints}
        L ${points[points.length - 1].x},${padding.top + chartHeight}
        Z
      `;

      // Dots and X Labels
      let dotsHTML = '';
      points.forEach((p, idx) => {
        dotsHTML += `
          <circle cx="${p.x}" cy="${p.y}" r="3.5" fill="#FFFFFF" stroke="${strokeColor}" stroke-width="2">
            <title>${p.label}: ${p.val}</title>
          </circle>
          ${idx % Math.ceil(points.length / 8) === 0 || idx === points.length - 1 ? `
            <text x="${p.x}" y="${padding.top + chartHeight + 16}" font-size="10" fill="${COLORS.navyMuted}" text-anchor="middle">${p.label}</text>
          ` : ''}
        `;
      });

      // Axis
      const axisHTML = `
        <line x1="${padding.left}" y1="${padding.top + chartHeight}" x2="${width - padding.right}" y2="${padding.top + chartHeight}" stroke="${COLORS.borderLight}" stroke-width="1.5"/>
        <line x1="${padding.left}" y1="${padding.top}" x2="${padding.left}" y2="${padding.top + chartHeight}" stroke="${COLORS.borderLight}" stroke-width="1.5"/>
        ${yLabel ? `<text x="${padding.left}" y="${padding.top - 10}" font-size="10" font-weight="600" fill="${COLORS.navyMuted}">${yLabel}</text>` : ''}
        ${xLabel ? `<text x="${width / 2}" y="${height - 4}" font-size="10" font-weight="600" fill="${COLORS.navyMuted}" text-anchor="middle">${xLabel}</text>` : ''}
      `;

      const gradId = `line-grad-${Math.random().toString(36).substr(2, 6)}`;

      el.innerHTML = `
        <svg viewBox="0 0 ${width} ${height}" width="100%" height="${height}" style="overflow: visible; display: block;" role="img" aria-label="${options.title || 'Line Chart'}">
          <title>${options.title || 'Line Chart'}</title>
          <desc>${options.desc || 'Line trend chart showing ' + (yLabel || 'metrics') + ' over sequence'}</desc>
          <defs>
            <linearGradient id="${gradId}" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="${strokeColor}" stop-opacity="0.25"/>
              <stop offset="100%" stop-color="${strokeColor}" stop-opacity="0.02"/>
            </linearGradient>
          </defs>
          ${gridHTML}
          ${fillGradient ? `<path d="${areaPath}" fill="url(#${gradId})"/>` : ''}
          <polyline points="${polylinePoints}" fill="none" stroke="${strokeColor}" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          ${dotsHTML}
          ${axisHTML}
        </svg>
      `;
    },

    /**
     * 4. Render Donut Chart
     * Suitable for Category Distribution (Emergency vs Scheduled vs Walk-in)
     */
    renderDonutChart(containerId, options = {}) {
      const el = getElement(containerId);
      if (!el) return;

      const {
        data = [],
        labelKey = 'label',
        valueKey = 'value',
        size = 200,
        thickness = 32,
        centerLabel = '',
        centerSub = 'Total',
        emptyMessage = 'No slice data'
      } = options;

      const total = data.reduce((sum, d) => sum + (Number(d[valueKey]) || 0), 0);

      if (total === 0) {
        el.innerHTML = `<div style="text-align:center; padding: 40px; color:${COLORS.navyMuted};">${emptyMessage}</div>`;
        return;
      }

      const radius = size / 2;
      const innerRadius = radius - thickness;
      let cumulativeAngle = -Math.PI / 2; // Start from top 12 o'clock

      let slicesHTML = '';
      let legendHTML = '<div style="display:flex; flex-direction:column; gap:8px; margin-left: 20px;">';

      data.forEach((d) => {
        const val = Number(d[valueKey]) || 0;
        if (val <= 0) return;

        const fraction = val / total;
        const angle = fraction * 2 * Math.PI;

        const x1 = radius + radius * Math.cos(cumulativeAngle);
        const y1 = radius + radius * Math.sin(cumulativeAngle);
        const x2 = radius + radius * Math.cos(cumulativeAngle + angle);
        const y2 = radius + radius * Math.sin(cumulativeAngle + angle);

        const x3 = radius + innerRadius * Math.cos(cumulativeAngle + angle);
        const y3 = radius + innerRadius * Math.sin(cumulativeAngle + angle);
        const x4 = radius + innerRadius * Math.cos(cumulativeAngle);
        const y4 = radius + innerRadius * Math.sin(cumulativeAngle);

        const largeArc = angle > Math.PI ? 1 : 0;

        const pathData = `
          M ${x1} ${y1}
          A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2}
          L ${x3} ${y3}
          A ${innerRadius} ${innerRadius} 0 ${largeArc} 0 ${x4} ${y4}
          Z
        `;

        const sliceColor = d.color || COLORS.tealPrimary;
        const pct = Math.round(fraction * 100);

        slicesHTML += `
          <path d="${pathData}" fill="${sliceColor}" stroke="#FFFFFF" stroke-width="2">
            <title>${d[labelKey]}: ${val} (${pct}%)</title>
          </path>
        `;

        legendHTML += `
          <div style="display:flex; align-items:center; gap:8px; font-size:0.8125rem;">
            <span style="width:10px; height:10px; border-radius:50%; background:${sliceColor}; flex-shrink:0;"></span>
            <span style="font-weight:600; color:${COLORS.navyDark};">${d[labelKey]}:</span>
            <span style="color:${COLORS.navyMuted}; font-weight:500;">${val} (${pct}%)</span>
          </div>
        `;

        cumulativeAngle += angle;
      });

      legendHTML += '</div>';

      const centerTextHTML = `
        <text x="${radius}" y="${radius - 2}" text-anchor="middle" font-size="20" font-weight="800" fill="${COLORS.navyDark}" font-family="system-ui, sans-serif">${centerLabel || total}</text>
        <text x="${radius}" y="${radius + 14}" text-anchor="middle" font-size="10" font-weight="600" fill="${COLORS.navyMuted}" text-transform="uppercase" font-family="system-ui, sans-serif">${centerSub}</text>
      `;

      el.innerHTML = `
        <div style="display:flex; align-items:center; justify-content:center; flex-wrap:wrap; gap:16px;">
          <svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" role="img" aria-label="${options.title || 'Donut Chart'}" style="display:block; flex-shrink:0;">
            <title>${options.title || 'Donut Chart'}</title>
            <desc>${options.desc || 'Donut chart showing proportional category breakdown'}</desc>
            ${slicesHTML}
            ${centerTextHTML}
          </svg>
          ${legendHTML}
        </div>
      `;
    }
  };
}));
