import * as React from "react";

import { useMutation } from "@tanstack/react-query";
import { LoaderCircle, PackageIcon } from "lucide-react";
import { useTranslation } from "react-i18next";

import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { usePickerPopover } from "~/hooks/use-picker-popover";
import { getDisplayName } from "~/lib/display";
import { extractErrorMessage } from "~/lib/error";
import { safeStringArray } from "~/lib/type-guards";
import { cn } from "~/lib/utils";
import api from "~/services/api";
import type { QuickMessage } from "~/types";
import { Button } from "~/components/ui/button";
import { Checkbox } from "~/components/ui/checkbox";
import {
  Popover,
  PopoverContent,
  PopoverTitle,
  PopoverTrigger,
} from "~/components/ui/popover";

import { PickerErrorAlert } from "./picker-error-alert";

export interface ExtensionPickerButtonProps {
  disabled?: boolean;
  className?: string;
}

function getQuickMessages(source: unknown): QuickMessage[] {
  if (!Array.isArray(source)) {
    return [];
  }

  return source.filter((item): item is QuickMessage =>
    Boolean(
      item &&
      typeof item === "object" &&
      typeof item.id === "string" &&
      typeof item.content === "string" &&
      item.content.trim().length > 0,
    ),
  );
}

export function ExtensionPickerButton({ disabled = false, className }: ExtensionPickerButtonProps) {
  const { t } = useTranslation("input");
  const { settings, currentAssistant } = useCurrentAssistant();
  const canUse = Boolean(settings && currentAssistant && !disabled);
  const { error, setError, popoverProps } = usePickerPopover(canUse);

  const [switchingId, setSwitchingId] = React.useState<string | null>(null);

  const quickMessages = React.useMemo(
    () => getQuickMessages(settings?.quickMessages),
    [settings?.quickMessages],
  );
  const quickMessageIdSet = React.useMemo(
    () => new Set(quickMessages.map((item) => item.id)),
    [quickMessages],
  );
  const selectedQuickMessageIds = React.useMemo(
    () => safeStringArray(currentAssistant?.quickMessageIds),
    [currentAssistant?.quickMessageIds],
  );

  const hasData = quickMessages.length > 0;
  const selectedCount = selectedQuickMessageIds.length;

  React.useEffect(() => {
    if (!canUse || !hasData) {
      popoverProps.onOpenChange(false);
    }
  }, [canUse, hasData]);

  const updateQuickMessages = useMutation({
    mutationFn: ({
      assistantId,
      quickMessageIds,
    }: {
      assistantId: string;
      quickMessageIds: string[];
    }) =>
      api.post<{ status: string }>("settings/assistant/quick-messages", {
        assistantId,
        quickMessageIds,
      }),
    onError: (updateError) => {
      setError(extractErrorMessage(updateError, t("quick_messages.update_failed")));
    },
    onSuccess: () => setError(null),
  });

  const handleToggleQuickMessage = React.useCallback(
    (id: string, checked: boolean) => {
      if (!canUse || !currentAssistant) return;
      const nextIds = new Set(
        selectedQuickMessageIds.filter((item) => quickMessageIdSet.has(item)),
      );
      if (checked) nextIds.add(id);
      else nextIds.delete(id);
      setSwitchingId(id);
      updateQuickMessages.mutate(
        { assistantId: currentAssistant.id, quickMessageIds: Array.from(nextIds) },
        { onSettled: () => setSwitchingId(null) },
      );
    },
    [
      canUse,
      currentAssistant,
      quickMessageIdSet,
      selectedQuickMessageIds,
      updateQuickMessages,
    ],
  );

  if (!hasData) {
    return null;
  }

  return (
    <Popover {...popoverProps}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          aria-label={t("quick_messages.title")}
          title={t("quick_messages.title")}
          variant="ghost"
          size="sm"
          disabled={!canUse || updateQuickMessages.isPending}
          className={cn(
            "h-8 rounded-full px-2 text-muted-foreground hover:text-foreground",
            selectedCount > 0 && "text-primary hover:bg-primary/10",
            className,
          )}
        >
          {updateQuickMessages.isPending ? (
            <LoaderCircle className="size-4 animate-spin" />
          ) : (
            <PackageIcon className="size-4" />
          )}
          {selectedCount > 0 ? (
            <span className="rounded-full bg-primary/10 px-1.5 py-0.5 text-[10px] text-primary">
              {selectedCount}
            </span>
          ) : null}
        </Button>
      </PopoverTrigger>

      <PopoverContent
        side="top"
        align="end"
        sideOffset={8}
        className="w-[min(92vw,20rem)] max-h-[var(--radix-popover-content-available-height)] overflow-y-auto space-y-3 p-4"
      >
        <PopoverTitle className="text-sm">{t("quick_messages.title")}</PopoverTitle>

        <div className="space-y-3">
          <PickerErrorAlert error={error} />

          <div className="max-h-64 space-y-2 overflow-y-auto">
            {quickMessages.map((item) => {
              const checked = selectedQuickMessageIds.includes(item.id);
              const switching = updateQuickMessages.isPending && switchingId === item.id;

              return (
                <label
                  key={item.id}
                  className={cn(
                    "flex cursor-pointer items-center gap-2 rounded-lg px-2 py-2 transition hover:bg-muted",
                    checked && "bg-primary/5",
                  )}
                >
                  {switching ? (
                    <LoaderCircle className="size-4 animate-spin" />
                  ) : (
                    <Checkbox
                      checked={checked}
                      disabled={disabled || updateQuickMessages.isPending}
                      onCheckedChange={(nextChecked) => {
                        handleToggleQuickMessage(item.id, Boolean(nextChecked));
                      }}
                    />
                  )}
                  <div className="min-w-0">
                    <div className="truncate text-sm font-medium">
                      {getDisplayName(item.title, t("quick_messages.unnamed"))}
                    </div>
                    <div className="text-muted-foreground mt-0.5 line-clamp-1 text-xs">
                      {item.content}
                    </div>
                  </div>
                </label>
              );
            })}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
