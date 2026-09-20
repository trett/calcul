# Product Guidelines: AI Calorie Tracker

## 1. Visual Identity & Design System
- **Design System:** Powered by Shoelace Web Components via `laminar-shoelace-components`. Clean, modern, accessible component primitives.
- **Color Palette & Theme:**
  - Neutral, calm backgrounds with high-contrast readable typography.
  - Light and dark mode support following system preference.
  - Color-coded calorie targets (e.g. green/neutral when on track, amber when approaching limit, soft red if exceeded).
- **Typography & Hierarchy:** Clean sans-serif font stack. Clear visual hierarchy with prominent calorie numbers and legible AI reasoning text.

## 2. User Experience & Interaction Principles
- **Mobile-First Responsiveness:** Optimized for quick on-the-go interactions (capturing a photo via camera on mobile or typing a quick meal note). Seamless scaling to tablet and desktop screens.
- **Frictionless Logging:**
  - Single-tap actions for common flows (photo snap / quick text entry).
  - Immediate optimistic UI feedback during upload and processing.
  - Clear progress/skeleton loading indicator while Gemini Flash evaluates the meal.
- **Review & Transparency:**
  - Always present the AI's itemized breakdown and rationale alongside the calorie estimate.
  - Allow frictionless one-click confirmation or quick manual adjustment of the number before finalizing.

## 3. Tone of Voice & Content Style
- **Tone:** Encouraging, objective, non-judgmental, and empowering. Focus on supportive habit-building rather than guilt.
- **Clarity & Brevity:** UI copy should be concise and actionable. Avoid jargon.
- **AI Explainability:** AI reasoning should be concise and transparent (e.g., *"Estimated ~420 kcal: 2 scrambled eggs (~180 kcal), 1 slice sourdough toast (~140 kcal), 1 tsp butter (~100 kcal)"*).

## 4. Accessibility & Performance
- **Accessibility:** Strict WCAG 2.1 AA compliance; full keyboard navigation, ARIA labeling provided by Shoelace components, and screen-reader friendly status updates.
- **Performance:** Instant client-side page transitions via Laminar; compact static asset bundles served by the backend; minimal latency on image upload and processing.
