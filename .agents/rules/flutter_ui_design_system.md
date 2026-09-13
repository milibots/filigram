---
description: Comprehensive Modern UI/UX and Product Design System for Flutter applications.
globs: ["**/*.dart", "**/pubspec.yaml"]
---

# Modern Flutter Product Design System

When building or updating Flutter applications, **NEVER** use default, stock, or generic Flutter/Material tutorial styling. Every UI must feel like a polished, Behance/Dribbble-grade production product with a cohesive, centralized design system.

## 1. UI Architecture & Stack

Prioritize well-maintained, high-leverage packages instead of manually reinventing basic primitives:
- **`google_fonts`**: Cohesive typography hierarchy (e.g. `Plus Jakarta Sans`, `Inter`, `Outfit`, or `Vazirmatn` for Persian/RTL).
- **`flutter_animate`**: Micro-interactions, soft entry transitions, and tactile button press feedback.
- **`lucide_icons`** / **`iconsax`**: Minimal, modern stroke icon sets (avoid stock Material icons).
- **`gap`**: Strict, readable layout spacing (`Gap(8)`, `Gap(16)`, `Gap(24)`).
- **`cached_network_image`**: Performant image loading with shimmer/blur placeholders.
- **`shimmer`**: Sleek skeleton loaders instead of generic circular spinners.
- **`go_router`**: Clean declarative routing and tab-shell navigation.

## 2. Design System Tokens (Theme Centralization)

All dimensions, radii, colors, and shadows MUST be defined centrally in a design system file (e.g. `core/theme/app_theme.dart`):

### Spacing Scale
- `xs`: 4.0
- `sm`: 8.0
- `md`: 16.0
- `lg`: 24.0
- `xl`: 32.0

### Border Radius Scale
- `radiusSm`: 12.0
- `radiusMd`: 16.0
- `radiusLg`: 24.0
- `radiusPill`: 999.0 (Full capsule/pill shape)

### Colors & Elevation
- Dark mode first or curated light palette with rich contrast.
- Subtle 1px borders (`Colors.white.withOpacity(0.08)`) with soft, diffused shadows (`blurRadius: 16`, `spreadRadius: 0`, opacity < 0.15).
- Avoid harsh gradients or high-saturation solid primaries.

## 3. Core Component Specs

### Buttons (Pills)
- Always rounded or pill-shaped (`BorderRadius.circular(AppRadii.pill)`).
- Primary CTA: High contrast, elegant padding (e.g. `H: 24, V: 14`), subtle scale-down on tap via `flutter_animate`.
- Secondary / Ghost Buttons: Subtle semi-transparent background (`0.06` opacity) with 1px soft border.

### Cards
- Radius of 16px to 24px with comfortable internal padding (16-20px).
- Clean separation using subtle border (`0.08` opacity) and soft ambient shadows.
- No harsh rectangular blocks.

### Navigation Bar
- Modern floating pill or docked blur navigation with rounded capsule indicators.
- Selected tab: Subtle pill highlight container behind icon/label.
- No default standard Flutter `BottomNavigationBar`.

### Custom Header
- Large, bold typography with crisp hierarchy.
- Integrated action buttons styled with pills or circular glass chips.
- Seamless blend with background (avoid blocky standard `AppBar` look).

## 4. Quality Standard
- Zero tutorial look: Every screen looks intentional, spacious, and responsive.
- Run `flutter analyze` after changes to ensure clean, warning-free code.
