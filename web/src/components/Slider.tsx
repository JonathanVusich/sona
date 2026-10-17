import { createSignal, type JSX } from "solid-js";

// How big a slider is drawn, as the class that sizes it.
export const SliderSize = {
  LARGE: "slider-large",
  SMALL: "slider-small",
} as const;

export type SliderSize = (typeof SliderSize)[keyof typeof SliderSize];

// Whether the slider takes input: the seek bar doesn't while nothing is playing.
export const SliderStatus = {
  ENABLED: "enabled",
  DISABLED: "disabled",
} as const;

export type SliderStatus = (typeof SliderStatus)[keyof typeof SliderStatus];

const Drag = {
  IDLE: "idle",
  DRAGGING: "dragging",
} as const;

type Drag = (typeof Drag)[keyof typeof Drag];

// How far each key moves the slider, in steps.
const KEY_STEPS: Record<string, number> = { ArrowRight: 1, ArrowUp: 1, ArrowLeft: -1, ArrowDown: -1 };

// Pointer-driven slider. The parent owns the value: onInput fires while dragging, onCommit on release or key press.
export default function Slider(props: {
  value: number;
  max: number;
  step: number;
  label: string;
  valueText: string;
  size: SliderSize;
  status: SliderStatus;
  onInput: (value: number) => void;
  onCommit: (value: number) => void;
}) {
  const [drag, setDrag] = createSignal<Drag>(Drag.IDLE);

  const ratio = () => {
    if (props.max <= 0) {
      return 0;
    }
    return Math.min(1, Math.max(0, props.value / props.max));
  };

  const valueAt = (slider: HTMLDivElement, clientX: number) => {
    const rect = slider.getBoundingClientRect();
    return Math.min(1, Math.max(0, (clientX - rect.left) / rect.width)) * props.max;
  };

  const onPointerDown: JSX.EventHandler<HTMLDivElement, PointerEvent> = (event) => {
    if (props.status === SliderStatus.DISABLED || event.button !== 0) {
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    setDrag(Drag.DRAGGING);
    props.onInput(valueAt(event.currentTarget, event.clientX));
  };

  const onPointerMove: JSX.EventHandler<HTMLDivElement, PointerEvent> = (event) => {
    if (drag() === Drag.DRAGGING) {
      props.onInput(valueAt(event.currentTarget, event.clientX));
    }
  };

  const onPointerUp: JSX.EventHandler<HTMLDivElement, PointerEvent> = (event) => {
    if (drag() === Drag.DRAGGING) {
      setDrag(Drag.IDLE);
      props.onCommit(valueAt(event.currentTarget, event.clientX));
    }
  };

  const onPointerCancel = () => {
    if (drag() === Drag.DRAGGING) {
      setDrag(Drag.IDLE);
      props.onCommit(props.value);
    }
  };

  // The value a key moves the slider to, or null for keys the slider doesn't use.
  const keyTarget = (key: string): number | null => {
    switch (key) {
      case "Home":
        return 0;
      case "End":
        return props.max;
    }
    const steps = KEY_STEPS[key];
    if (steps === undefined) {
      return null;
    }
    return props.value + steps * props.step;
  };

  const onKeyDown = (event: KeyboardEvent) => {
    const target = keyTarget(event.key);
    if (target === null || props.status === SliderStatus.DISABLED) {
      return;
    }
    event.preventDefault();
    props.onCommit(Math.min(props.max, Math.max(0, target)));
  };

  return (
    <div
      class={`slider ${props.size}`}
      classList={{ dragging: drag() === Drag.DRAGGING, disabled: props.status === SliderStatus.DISABLED }}
      role="slider"
      tabindex={sliderTabIndex(props.status)}
      aria-label={props.label}
      aria-valuemin={0}
      aria-valuemax={props.max}
      aria-valuenow={props.value}
      aria-valuetext={props.valueText}
      aria-disabled={props.status === SliderStatus.DISABLED}
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

// A disabled slider is left out of the tab order.
function sliderTabIndex(status: SliderStatus): number {
  switch (status) {
    case SliderStatus.ENABLED:
      return 0;
    case SliderStatus.DISABLED:
      return -1;
  }
}
