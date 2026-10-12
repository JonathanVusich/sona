import { render } from "solid-js/web";
import App from "./App";
import { restoreSession } from "./auth";
import { applyTheme, loadTheme } from "./theme";
import "./styles.css";

applyTheme(loadTheme());
// Before the first render, so a reload doesn't flash the login page.
await restoreSession();

const root = document.getElementById("root");
if (root === null) {
  throw new Error("index.html has no #root element to render into.");
}
render(() => <App />, root);
