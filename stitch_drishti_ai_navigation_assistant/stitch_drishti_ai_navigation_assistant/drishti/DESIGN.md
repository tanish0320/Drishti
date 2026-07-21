---
name: Drishti
colors:
  surface: '#fcf9f8'
  surface-dim: '#dcd9d9'
  surface-bright: '#fcf9f8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f3f2'
  surface-container: '#f0eded'
  surface-container-high: '#eae7e7'
  surface-container-highest: '#e5e2e1'
  on-surface: '#1b1c1c'
  on-surface-variant: '#414752'
  inverse-surface: '#303030'
  inverse-on-surface: '#f3f0ef'
  outline: '#717783'
  outline-variant: '#c1c6d4'
  surface-tint: '#005faf'
  primary: '#005dac'
  on-primary: '#ffffff'
  primary-container: '#1976d2'
  on-primary-container: '#fffdff'
  inverse-primary: '#a5c8ff'
  secondary: '#1b6d24'
  on-secondary: '#ffffff'
  secondary-container: '#a0f399'
  on-secondary-container: '#217128'
  tertiary: '#9a4300'
  on-tertiary: '#ffffff'
  tertiary-container: '#c05600'
  on-tertiary-container: '#fffdff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d4e3ff'
  primary-fixed-dim: '#a5c8ff'
  on-primary-fixed: '#001c3a'
  on-primary-fixed-variant: '#004786'
  secondary-fixed: '#a3f69c'
  secondary-fixed-dim: '#88d982'
  on-secondary-fixed: '#002204'
  on-secondary-fixed-variant: '#005312'
  tertiary-fixed: '#ffdbca'
  tertiary-fixed-dim: '#ffb68f'
  on-tertiary-fixed: '#331200'
  on-tertiary-fixed-variant: '#773200'
  background: '#fcf9f8'
  on-background: '#1b1c1c'
  surface-variant: '#e5e2e1'
typography:
  headline-lg:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 40px
    fontWeight: '800'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 32px
    fontWeight: '800'
    lineHeight: 40px
  headline-md:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-sm:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  body-lg:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 22px
    fontWeight: '400'
    lineHeight: 32px
  body-md:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 20px
    fontWeight: '400'
    lineHeight: 28px
  label-lg:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0.5px
  label-md:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 20px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  touch-target-min: 64dp
  gutter: 24px
  margin-mobile: 24px
  margin-tablet: 48px
  stack-gap: 20px
---

## Brand & Style

This design system is engineered for maximum legibility and accessibility, prioritizing users with visual impairments. The brand personality is dependable, clear, and uncompromisingly functional. 

The aesthetic is **High-Contrast Minimalism**. By stripping away non-functional decorative elements like gradients and complex shadows, the system ensures that the information hierarchy is immediately apparent. The UI leverages generous whitespace and robust structural elements to create a calm, professional environment that reduces cognitive load and facilitates ease of navigation via screen readers and physical touch.

## Colors

The palette is anchored by a high-contrast white background to ensure a "Paper-white" reading experience. 

- **Primary Blue (#1976D2):** Used for main actions and branding elements. It provides a strong, recognizable signal for interactivity.
- **Success Green (#2E7D32):** Reserved for "safe" states, completed tasks, and active connections.
- **Warning Orange (#EF6C00):** Used for non-critical alerts or states requiring user attention.
- **Critical Red (#D32F2F):** Strictly for destructive actions or dangerous system states.

All text must maintain a minimum contrast ratio of 7:1 against the background. Surface containers utilize light grey borders (#E0E0E0) rather than shadows to define boundaries.

## Typography

The design system utilizes **Atkinson Hyperlegible Next** (as a high-clarity alternative to Inter) to ensure that similar character shapes are easily distinguishable. 

Typography is scaled significantly larger than standard Material 3 specifications. The minimum body size is set to 20px to accommodate low-vision users. Headlines are bold and heavy to provide clear landmarks when scanning a page. Avoid all-caps styling for long strings of text as it hinders shape recognition; reserve capitalization for short, high-importance labels only.

## Layout & Spacing

The layout follows a **Fluid Grid** model with significantly increased gutter widths to prevent accidental taps. 

- **Touch Targets:** A strict minimum of 64dp for all interactive elements (buttons, toggles, list items).
- **Safe Areas:** Generous 24px outer margins on mobile to ensure content does not bleed into screen edges or interfere with hand-holding positions.
- **Vertical Rhythm:** Elements are stacked with a minimum 20px gap to ensure visual separation between distinct functional blocks.
- **Reflow:** On tablet and desktop, content does not stretch excessively wide; instead, it centers within a maximum container width of 800px to maintain a comfortable line length for reading.

## Elevation & Depth

In alignment with high-contrast requirements, this design system avoids soft ambient shadows which can appear "blurry" to visually impaired users.

Depth is communicated through **Low-Contrast Outlines** and **Tonal Layering**:
1. **Level 0 (Base):** Pure white (#FFFFFF).
2. **Level 1 (Cards/Containers):** White background with a 2px solid border (#212121 or #E0E0E0).
3. **Focus State:** When an element is focused (via TalkBack or keyboard), it receives a 4px high-visibility stroke using the Primary Blue color.

This "Flat-Plus" approach ensures that every interactive boundary is sharp and definite.

## Shapes

The design system uses a **Rounded** shape language to make the interface feel approachable and to clearly differentiate UI containers from the rectangular screen edges. 

Standard components (buttons, input fields) utilize a **16px (rounded-lg)** corner radius. Large surface containers and cards utilize a **24px (rounded-xl)** radius. This consistency helps users identify "grouped" information through the repetition of curved enclosures.

## Components

### Buttons
- **Primary:** Solid Primary Blue background with White text. Minimum height 64dp.
- **Secondary:** White background with 2px Primary Blue border.
- **Icons:** Must be paired with a text label unless the icon is a standard "Back" or "Close" button, which must have a large touch slop and explicit content descriptions for screen readers.

### Input Fields
- Use "Outlined" style with a 2px stroke. 
- Labels remain visible at all times (no floating labels that disappear). 
- Hint text must have at least 4.5:1 contrast.

### Cards
- Used to group related content. 
- Must have a 2px border (#E0E0E0). 
- The entire card area should be the touch target if it leads to a detail view.

### Lists
- List items have a minimum height of 72dp.
- Use 24px icons and 20px primary text.
- Dividers are 2px thick to clearly separate rows.

### Accessibility Cues
- Every image requires an `contentDescription`.
- State changes (e.g., "Scanning started", "Item added") must trigger an `AccessibilityLiveRegion` announcement.
- Active states (like a toggle being 'On') should use both color (Green) and a secondary visual indicator (a checkmark icon).