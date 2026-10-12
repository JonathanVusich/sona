import { render } from "solid-js/web";
import App from "./App";
import { restoreSession } from "./auth";
import { applyTheme, loadTheme } from "./theme";
import "./styles.css";

applyTheme(loadTheme());
// Before the first render, so a reload doesn't flash the login page.
await restoreSession();

render(() => <App />, document.getElementById("root")!);
