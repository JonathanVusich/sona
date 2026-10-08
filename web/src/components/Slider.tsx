import { createSignal } from "solid-js";

// Pointer-driven slider. The parent owns the value: onInput fires while dragging, onCommit on release or key press.
export default function Slider(props: {
  value: number;
  max: number;
  step: number;
  label: string;
  valueText: string;
  size: "large" | "small";
  disabled?: boolean;
  onInput: (value: number) => void;
  onCommit: (value: number) => void;
}) {
  const [dragging, setDragging] = createSignal(false);
  let element!: HTMLDivElement;

  const ratio = () => (props.max > 0 ? Math.min(1, Math.max(0, props.value / props.max)) : 0);

  const valueAt = (clientX: number) => {
    const rect = element.getBoundingClientRect();
    return Math.min(1, Math.max(0, (clientX - rect.left) / rect.width)) * props.max;
  };

  const onPointerDown = (event: PointerEvent) => {
    if (props.disabled || event.button !== 0) {
      return;
    }
    element.setPointerCapture(event.pointerId);
    setDragging(true);
    props.onInput(valueAt(event.clientX));
  };

  const onPointerMove = (event: PointerEvent) => {
    if (dragging()) {
      props.onInput(valueAt(event.clientX));
    }
  };

  const onPointerUp = (event: PointerEvent) => {
    if (dragging()) {
      setDragging(false);
      props.onCommit(valueAt(event.clientX));
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
      ref={element}
      class={`slider slider-${props.size}`}
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
