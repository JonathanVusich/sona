import { Client, type IMessage } from "@stomp/stompjs";
import { createEffect, createMemo, createRoot, createSignal, on } from "solid-js";
import { Permission, type IngestState, type TrackIngestResponse } from "./api/responses";
import { ApiRoutes } from "./api/routes";
import { connectHeadersAsCurrentUser, currentUser, hasPermission } from "./auth";

// Follows how far Sona has got ingesting the files this user uploaded, over a STOMP connection kept open while an
// uploader is signed in. Sona sends each new state as it's given, with no catch-up, so a state sent while the
// connection was down is missed until the next one.

// Each ingest's latest state, by its trackIngestId.
const [ingestStates, setIngestStates] = createSignal<ReadonlyMap<string, IngestState>>(new Map());

const socket = new Client({
  brokerURL: socketUrl(),
  // Each attempt sends the token the session holds then, which refreshes keep current.
  beforeConnect: (client) => {
    client.connectHeaders = connectHeadersAsCurrentUser();
  },
  onConnect: () => {
    socket.subscribe(ApiRoutes.INGEST_PROGRESS, storeIngestState);
  },
});

// Sona authenticates a connection once, when it opens, so a different user signing in needs a connection of their own.
createRoot(() => {
  const uploaderId = createMemo(() => {
    if (!hasPermission(Permission.LIBRARY_UPLOAD)) {
      return null;
    }
    return currentUser()?.userId ?? null;
  });
  createEffect(on(uploaderId, connectAs));
});

/**
 * @returns the latest state Sona sent for the ingest, or null when it has sent none since the states were forgotten
 */
export function ingestState(trackIngestId: string): IngestState | null {
  return ingestStates().get(trackIngestId) ?? null;
}

/**
 * Forgets the states sent so far, once the uploads they were for are cleared.
 */
export function forgetIngestStates() {
  setIngestStates(new Map());
}

async function connectAs(uploaderId: string | null): Promise<void> {
  await socket.deactivate();
  if (uploaderId !== null) {
    socket.activate();
  }
}

function storeIngestState(message: IMessage) {
  const ingest: TrackIngestResponse = JSON.parse(message.body);
  setIngestStates((current) => new Map(current).set(ingest.trackIngestId, ingest.state));
}

function socketUrl(): string {
  const url = new URL(ApiRoutes.SOCKET, window.location.href);
  url.protocol = socketProtocol(url.protocol);
  return url.href;
}

function socketProtocol(protocol: string): string {
  if (protocol === "https:") {
    return "wss:";
  }
  return "ws:";
}
