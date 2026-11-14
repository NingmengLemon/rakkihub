import type { ComponentPropsWithRef } from "react";

export default function Logo(props: ComponentPropsWithRef<"img">) {
  return (
    <img src="/icon.png" alt="RakkiHub" width={256} height={256} draggable={false} {...props} />
  );
}
