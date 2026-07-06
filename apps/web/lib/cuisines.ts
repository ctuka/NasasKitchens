/** Shared cuisine tag → label/icon (front-end-spec.md cuisine filter pills). */
export const CUISINES: { tag: string; label: string; icon: string }[] = [
  { tag: "turkish", label: "Turkish", icon: "🇹🇷" },
  { tag: "chinese", label: "Chinese", icon: "🇨🇳" },
  { tag: "mexican", label: "Mexican", icon: "🇲🇽" },
  { tag: "indian", label: "Indian", icon: "🇮🇳" },
  { tag: "italian", label: "Italian", icon: "🇮🇹" },
  { tag: "japanese", label: "Japanese", icon: "🇯🇵" },
  { tag: "korean", label: "Korean", icon: "🇰🇷" },
  { tag: "vietnamese", label: "Vietnamese", icon: "🇻🇳" },
  { tag: "lebanese", label: "Lebanese", icon: "🇱🇧" },
  { tag: "ethiopian", label: "Ethiopian", icon: "🇪🇹" },
  { tag: "persian", label: "Persian", icon: "🇮🇷" },
  { tag: "greek", label: "Greek", icon: "🇬🇷" },
  { tag: "thai", label: "Thai", icon: "🇹🇭" },
  { tag: "other", label: "Other", icon: "🍽️" },
];

export const CUISINE_ICONS = Object.fromEntries(CUISINES.map((c) => [c.tag, c.icon]));
export const CUISINE_LABELS = Object.fromEntries(CUISINES.map((c) => [c.tag, c.label]));
