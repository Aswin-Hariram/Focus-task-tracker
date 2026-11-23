# Focus — Modern Productivity SaaS UI Design Guidelines

## 1. Selected Visual Direction

**Modern Productivity Enterprise** — a premium, modern, visually engaging productivity SaaS interface inspired by the approved Focus Dashboard and Daily Board designs.

The interface should combine enterprise-grade usability with a friendly, visually appealing productivity experience.

### Design Characteristics

- Dark navy sidebar with blue active navigation states.
- Bright, spacious content areas with subtle backgrounds.
- Nature-inspired landscape illustrations in welcome banners.
- Soft pastel gradients for statistics and workflow sections.
- Modern rounded cards with subtle shadows.
- Clean typography and well-defined visual hierarchy.
- Color-coded categories, priorities, and task statuses.
- Compact, intuitive controls with consistent iconography.
- Smooth, lightweight hover effects and transitions.
- Professional appearance without excessive decoration.

**Important:** The approved dashboard and Daily Board designs are the primary visual references. Maintain their overall appearance, proportions, colors, spacing, and component styling throughout the application.

---

## 2. Technology Stack

| Layer | Technology |
|---|---|
| Rendering | Server-rendered Thymeleaf |
| UI Framework | Bootstrap 5 |
| Styling | Custom CSS + Bootstrap utilities |
| Interactivity | Vanilla JavaScript |
| Icons | Lucide Icons |
| Typography | Inter, system sans-serif fallback |
| Illustrations | SVG and optimized static assets |
| Responsive Layout | Bootstrap Grid + CSS Grid/Flexbox |

### Restrictions

- Do not introduce React, Vue, Angular, or other frontend frameworks.
- Do not introduce unnecessary JavaScript libraries.
- Avoid inline styles and duplicated CSS.
- Preserve existing backend functionality, routes, APIs, and Thymeleaf bindings.
- Use progressive enhancement where appropriate.
- Prefer reusable components over page-specific implementations.

---

## 3. Design System

### 3.1 Color Palette

#### Core Colors

| Purpose | Color |
|---|---|
| Primary Blue | `#2563EB` |
| Primary Hover | `#1D4ED8` |
| Sidebar Background | `#0F172A` |
| Sidebar Active | `#1E3A5F` |
| Page Background | `#F8FAFC` |
| Card Background | `#FFFFFF` |
| Primary Text | `#0F172A` |
| Secondary Text | `#64748B` |
| Borders | `#E2E8F0` |

#### Pastel Component Colors

| Component | Background | Accent |
|---|---|---|
| Tasks Today | `#EFF6FF` | `#2563EB` |
| In Progress | `#FFF7ED` | `#F59E0B` |
| Completed | `#ECFDF5` | `#16A34A` |
| Overdue | `#FFF1F2` | `#DC2626` |
| Learning | `#ECFEFF` | `#0891B2` |
| Personal | `#F5F3FF` | `#9333EA` |

Use soft gradients and decorative background shapes sparingly. Pastel backgrounds must not reduce text readability.

### 3.2 CSS Design Tokens

Define shared variables in a centralized stylesheet.

```css
:root {
  --primary: #2563EB;
  --primary-hover: #1D4ED8;

  --sidebar-bg: #0F172A;
  --sidebar-active: #1E3A5F;

  --page-bg: #F8FAFC;
  --surface: #FFFFFF;

  --text-primary: #0F172A;
  --text-secondary: #64748B;

  --border-color: #E2E8F0;

  --success: #16A34A;
  --warning: #F59E0B;
  --danger: #DC2626;

  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 20px;

  --shadow-card:
    0 4px 16px rgba(15, 23, 42, 0.05);

  --shadow-hover:
    0 8px 24px rgba(15, 23, 42, 0.08);

  --transition: 180ms ease;

  --sidebar-width: 260px;
  --header-height: 76px;
}
```

---

## 4. Application Layout

### 4.1 Sidebar

Use a fixed dark navy sidebar on desktop.

**Navigation Structure**

Workspace:
- Dashboard
- Daily Board
- Tasks
- Overdue
- History

Insights:
- Analytics
- Categories

Bottom:
- Settings

#### Sidebar Styling

- Background: `#0F172A`
- Width: approximately 260px
- White application logo and title.
- Muted inactive navigation labels.
- Blue highlighted active navigation item.
- Rounded navigation backgrounds.
- Consistent Lucide outline icons.
- Subtle hover feedback.
- Settings positioned at the bottom.

On smaller screens, transform the sidebar into a collapsible drawer with accessible keyboard and touch controls.

### 4.2 Top Navigation Bar

The top navigation should contain:

1. Current weekday and formatted date.
2. Global task search field.
3. Dark/light mode toggle.
4. Primary **New Task** button.
5. User avatar or profile menu.

#### Global Search

Use a prominent rounded search field.

Placeholder:

`Search tasks, categories, or notes...`

Features:
- Magnifying-glass icon.
- Keyboard shortcut: `Ctrl + K` / `Cmd + K`.
- Search by task name, description, or category.
- Debounced input when live searching.
- Clear search control.
- Keyboard-accessible results.
- Proper empty and loading states.

Search should use existing server endpoints or backend services where available.

---

## 5. Dashboard Design

The Dashboard should follow the approved illustrated productivity dashboard design.

### 5.1 Welcome Banner

Create a wide, visually appealing greeting card.

Example:

**Good Morning, Aswin! 👋**

*A current view of planned work, progress, and deadlines.*

#### Visual Design

- Mint-green and light-blue gradient background.
- Nature-inspired SVG landscape.
- Layered hills, trees, clouds, and subtle birds.
- Rounded corners around 16–20px.
- Greeting positioned on the left.
- Decorative illustration positioned behind or beside the content.
- Maintain strong text contrast.

The greeting should change according to the user's local time:

- Good Morning
- Good Afternoon
- Good Evening

Use the authenticated user's display name.

The landscape illustration should be reusable and optimized, not generated dynamically for each page load.

### 5.2 Calendar Widget

Position a compact calendar beside the welcome banner on desktop.

Features:
- Current month and year.
- Previous and next navigation.
- Current date highlighting.
- Task activity indicators.
- Selectable dates.
- Selected-date task navigation.

Use a clean white card with rounded corners and subtle shadows.

### 5.3 Statistics Cards

Display four cards in one row on large screens.

| Card | Accent | Icon |
|---|---|---|
| Tasks Today | Blue | Clipboard Check |
| In Progress | Amber | Hourglass |
| Completed | Green | Circle Check |
| Overdue | Red | Alert Triangle |

Each card should contain:
- Soft pastel background or subtle gradient.
- Rounded colored icon container.
- Metric label.
- Large numerical value.
- Short explanatory text.
- Optional subtle decorative wave.

Values must be dynamically populated from the backend.

Use responsive layouts:
- Desktop: 4 columns.
- Tablet: 2 columns.
- Mobile: 1 or 2 columns depending on available width.

### 5.4 Today's Work

This is the primary dashboard content section.

Use a large white card with:

- Colored section icon.
- **Today's Work** heading.
- Short supporting description.
- View Board button.
- Optional filtering and sorting controls.

#### Task List

Each task row should contain:

- Completion checkbox.
- Task title.
- Category name and colored indicator.
- Current status badge.
- Deadline when available.
- Optional priority badge.
- Chevron for task details.

Use subtle separators and consistent row heights.

#### Interaction

- Checkbox updates task completion.
- Clicking a task opens its details.
- Status indicators reflect actual backend state.
- Hover highlights the row.
- Keyboard navigation remains accessible.
- Show clear feedback for successful or failed updates.

Avoid unnecessary full-page reloads for simple interactions when lightweight JavaScript can safely update the interface.

### 5.5 Completion Rate

Create a compact card containing:

- Target icon.
- Completion Rate heading.
- Current completion percentage.
- Horizontal progress bar.
- Optional small decorative analytics illustration.

Use green or blue progress indicators consistent with the approved design.

Calculate completion percentage using actual task data and clearly define whether it represents all tasks or only today's tasks.

### 5.6 Overdue Section

Display overdue tasks in a dedicated card.

When there are no overdue tasks, use a friendly empty state:

**No tasks are overdue.**

Include a lightweight clipboard/checkmark illustration.

Do not leave large blank areas without useful information.

---

## 6. Daily Board Design

The Daily Board should use the approved modern Kanban layout.

### 6.1 Page Header

Display a compact nature-inspired header banner containing:

**Daily Board**

*Track today's work across statuses with rapid updates and date navigation.*

Place a small daily summary panel beside it.

### 6.2 Daily Summary

Show:

- Total tasks.
- Completed tasks.
- Daily completion percentage.

Use a compact circular progress indicator or ring for completion.

All values must come from the backend.

### 6.3 Board Toolbar

Include:

- Category filter.
- Previous day button.
- Selected date.
- Next day button.
- Optional search or sorting controls.

Date navigation must update the displayed board data.

### 6.4 Kanban Columns

Use three primary columns:

**Yet to Start**
- Blue accent.
- Light-blue column background.
- Circular pending icon.

**In Progress**
- Amber accent.
- Light-amber column background.
- Hourglass icon.

**Completed**
- Green accent.
- Light-green column background.
- Check-circle icon.

Each column should contain:
- Column heading.
- Status icon.
- Dynamic task count.
- Task cards.
- Friendly empty state when no tasks exist.

#### Responsive Behavior

- Desktop: three columns.
- Tablet: two columns or horizontal scrolling.
- Mobile: stacked columns or accessible horizontal scrolling.

Do not allow task cards to become excessively narrow.

### 6.5 Kanban Task Cards

Each card should contain:

1. Category badge with colored dot.
2. Rolled-over indicator when applicable.
3. Task title.
4. Task description when available.
5. Current status.
6. Completion percentage.
7. Progress bar.
8. Priority badge.
9. Due date.
10. Contextual action buttons.

#### Status Actions

Yet to Start:
- Start
- Done

In Progress:
- Done
- Reset

Completed:
- Reopen

Use clearly differentiated action colors.

All actions must preserve the existing backend business logic.

### 6.6 Card Styling

- White background.
- Border radius: 12–16px.
- Subtle border and shadow.
- Internal padding: 16–20px.
- Consistent spacing between cards.
- Soft hover elevation.
- Clearly visible keyboard focus.
- Compact, readable badges.

Do not overcrowd cards with unnecessary controls.

### 6.7 Status Progress Colors

| Status | Progress Color |
|---|---|
| Yet to Start | Neutral gray |
| In Progress | Blue |
| Completed | Green |

Progress percentages must reflect real task state.

Do not hardcode percentages.

---

## 7. Shared UI Components

Create reusable components for:

- Sidebar
- Top navigation
- Global search
- Page header
- Welcome banner
- Statistic cards
- Calendar
- Task cards
- Status badges
- Category badges
- Priority badges
- Progress bars
- Filter dropdowns
- Date navigation
- Empty states
- Confirmation dialogs
- Loading indicators
- Toast notifications

Components should share design tokens and consistent visual behavior.

---

## 8. Typography and Spacing

### Typography

Use **Inter** as the primary font.

Recommended hierarchy:

| Element | Size |
|---|---|
| Main Page Heading | 28–32px |
| Section Heading | 18–22px |
| Card Heading | 16–18px |
| Body Text | 14–16px |
| Secondary Text | 13–14px |
| Badge Text | 12–13px |
| Statistic Value | 28–36px |

Use medium and semibold font weights for headings.

Avoid excessive bold text.

### Spacing

Use a consistent spacing scale:

`4px, 8px, 12px, 16px, 20px, 24px, 32px`

Recommended:
- Page padding: 24–32px desktop.
- Card padding: 16–24px.
- Card gap: 16–20px.
- Section spacing: 24–32px.

Maintain comfortable density without excessive whitespace.

---

## 9. Animations and Interactions

Use subtle transitions for:

- Card hover states.
- Button hover states.
- Sidebar navigation.
- Dropdown menus.
- Progress updates.
- Status changes.
- Toast notifications.
- Modal appearance.

Recommended transition duration:

`150ms–200ms`

Avoid:
- Excessive bouncing.
- Continuous decorative animation.
- Large layout shifts.
- Heavy JavaScript animation libraries.

Respect the `prefers-reduced-motion` accessibility setting.

---

## 10. Accessibility Requirements

- Meet WCAG 2.2 AA where practical.
- Maintain accessible text contrast.
- Provide visible focus indicators.
- Ensure keyboard navigation.
- Use semantic HTML elements.
- Provide accessible labels for icon-only controls.
- Use appropriate ARIA attributes.
- Ensure buttons have sufficiently large touch targets.
- Do not rely on color alone to communicate task status.
- Announce important asynchronous status updates appropriately.
- Ensure modal dialogs manage focus correctly.

---

## 11. Dark Mode

Support both light and dark themes.

### Light Theme

- Light page background.
- White cards.
- Navy sidebar.
- Blue primary actions.
- Pastel statistics and status colors.

### Dark Theme

- Dark page background.
- Dark elevated card surfaces.
- Accessible muted text.
- Adjusted pastel accents for dark surfaces.
- Consistent sidebar styling.

Use CSS custom properties for theme switching.

Store the user's theme preference when appropriate and respect system preferences when no explicit choice exists.

---

## 12. Thymeleaf Architecture

Organize reusable fragments and static assets.

Suggested structure:

```text
src/main/resources/
├── templates/
│   ├── fragments/
│   │   ├── sidebar.html
│   │   ├── topbar.html
│   │   ├── search.html
│   │   ├── page-header.html
│   │   ├── task-card.html
│   │   ├── stat-card.html
│   │   └── notifications.html
│   │
│   ├── dashboard.html
│   ├── daily-board.html
│   ├── tasks.html
│   ├── overdue.html
│   ├── history.html
│   ├── analytics.html
│   ├── categories.html
│   └── settings.html
│
└── static/
    ├── css/
    │   ├── tokens.css
    │   ├── layout.css
    │   ├── components.css
    │   ├── dashboard.css
    │   ├── daily-board.css
    │   └── themes.css
    │
    ├── js/
    │   ├── app.js
    │   ├── search.js
    │   ├── dashboard.js
    │   ├── daily-board.js
    │   └── theme.js
    │
    └── images/
        ├── landscape-banner.svg
        └── empty-tasks.svg
```

Adapt the structure to the existing project rather than unnecessarily reorganizing working code.

---

## 13. Implementation Workflow

1. Follow the existing `.claude` UI skill and design-quality process.
2. Treat the approved Focus dashboard and Daily Board screenshots as the primary visual references.
3. Do not reselect the visual style.
4. Inspect the existing Thymeleaf templates, stylesheets, JavaScript, and backend routes.
5. Establish shared design tokens and reusable components.
6. Implement the global sidebar and top navigation.
7. Implement the Dashboard design.
8. Implement the Daily Board design.
9. Apply the same design language to remaining pages.
10. Verify responsive layouts and interactive behavior.
11. Test keyboard navigation and accessibility.
12. Confirm that all existing backend functionality remains intact.

### Quality Checklist

- [ ] Matches the approved visual references.
- [ ] Consistent sidebar and top navigation.
- [ ] Shared design tokens are used.
- [ ] Responsive on desktop, tablet, and mobile.
- [ ] Functional global search.
- [ ] Dynamic task data and statistics.
- [ ] Working task status actions.
- [ ] Consistent category and priority colors.
- [ ] Accessible focus and hover states.
- [ ] Working light and dark themes.
- [ ] No unnecessary frontend frameworks.
- [ ] No regression in existing backend functionality.

---

## 14. Instruction for Claude

> Use **Modern Productivity Enterprise** as the approved visual direction for the Focus application.
>
> The supplied redesigned Dashboard and Daily Board screenshots are the primary visual references. Reproduce their visual language as closely as practical, including the dark navy sidebar, modern top navigation with global search, illustrated landscape banners, pastel statistics cards, rounded white task cards, color-coded Kanban columns, compact progress indicators, and polished empty states.
>
> Build the application using **server-rendered Thymeleaf, Bootstrap 5, custom CSS, Lucide icons, and lightweight vanilla JavaScript**.
>
> Do not introduce React, Vue, Angular, or unnecessary frontend frameworks.
>
> Before implementing pages, establish shared CSS design tokens, layout components, and reusable Thymeleaf fragments.
>
> Preserve all existing backend routes, APIs, data bindings, task operations, authentication, and business logic.
>
> Prioritize visual consistency, accessibility, responsiveness, and maintainability.
>
> The interface must feel like a premium modern productivity application rather than a generic Bootstrap admin dashboard.
>
> **Do not redesign the approved layouts into a different visual style. Use the reference screenshots as the visual source of truth.**