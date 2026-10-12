import { createSignal, type JSX } from "solid-js";

// How big a slider is drawn, as the class that sizes it.
export const SliderSize = {
  LARGE: "slider-large",
  SMALL: "slider-small",
} as const;

export type SliderSize = (typeof SliderSize)[keyof typeof SliderSize];

// Pointer-driven slider. The parent owns the value: onInput fires while dragging, onCommit on release or key press.
export default function Slider(props: {
  value: number;
  max: number;
  step: number;
  label: string;
  valueText: string;
  size: SliderSize;
  disabled?: boolean;
  onInput: (value: number) => void;
  onCommit: (value: number) => void;
}) {
  const [dragging, setDragging] = createSignal(false);

  const ratio = () => (props.max > 0 ? Math.min(1, Math.max(0, props.value / props.max)) : 0);

  const valueAt = (slider: HTMLDivElement, clientX: number) => {
    const rect = slider.getBoundingClientRect();
    return Math.min(1, Math.max(0, (clientX - rect.left) / rect.width)) * props.max;
  };

  const onPointerDown: JSX.EventHandler<HTMLDivElement, PointerEvent> = (event) => {
    if (props.disabled || event.button !== 0) {
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);
    props.onInput(valueAt(event.currentTarget, event.clientX));
  };

  const onPointerMove: JSX.EventHandler<HTMLDivElement, PointerEvent> = (event) => {
    if (dragging()) {
      props.onInput(valueAt(event.currentTarget, event.clientX));
    }
  };

  const onPointerUp: JSX.EventHandler<HTMLDivElement, PointerEvent> = (event) => {
    if (dragging()) {
      setDragging(false);
      props.onCommit(valueAt(event.currentTarget, event.clientX));
    }
  };

  const onPointerCancel = () => {
    if (dragging()) {
      setDragging(false);
      props.onCommit(props.value);
    }
  };

  const onKeyDown = (event: KeyboardEvent) => {
    const steps: Record<string, number> = { ArrowRight: 1, ArrowUp: 1, ArrowLeft: -1, ArrowDown: -1 };
    const target =
      event.key in steps
        ? props.value + steps[event.key] * props.step
        : event.key === "Home"
          ? 0
          : event.key === "End"
            ? props.max
            : undefined;
    if (target === undefined || props.disabled) {
      return;
    }
    event.preventDefault();
    props.onCommit(Math.min(props.max, Math.max(0, target)));
  };

  return (
    <div
      class={`slider ${props.size}`}
      classList={{ dragging: dragging(), disabled: props.disabled === true }}
      role="slider"
      tabindex={props.disabled ? -1 : 0}
      aria-label={props.label}
      aria-valuemin={0}
      aria-valuemax={props.max}
      aria-valuenow={props.value}
      aria-valuetext={props.valueText}
      aria-disabled={props.disabled}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      onKeyDown={onKeyDown}
    >
      <div class="slider-rail">
        <div class="slider-fill" style={{ transform: `scaleX(${ratio()})` }} />
      </div>
      {/* Moved with transforms only, so dragging never triggers layout. */}
      <div class="slider-thumb-track" style={{ transform: `translateX(${ratio() * 100}%)` }}>
        <div class="slider-thumb" />
      </div>
    </div>
  );
}
