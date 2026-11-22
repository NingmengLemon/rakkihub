import { useTranslation } from "react-i18next";

// Shown both while the Mermaid engine chunk loads and while it renders, so the
// fallback and the plugin component share one translated placeholder.
export function MermaidLoading() {
  const { t } = useTranslation("markdown");
  return (
    <div className="my-4 flex min-h-[160px] items-center justify-center text-muted-foreground text-sm">
      {t("mermaid.loading")}
    </div>
  );
}
