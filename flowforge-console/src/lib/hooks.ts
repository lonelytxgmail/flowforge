import { useEffect, useState } from "react";

type AsyncState<T> = {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
};

export function useAsyncData<T>(loader: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [reloadKey, setReloadKey] = useState(0);
  const [state, setState] = useState<AsyncState<T>>({
    data: null,
    loading: true,
    error: null,
    reload: () => setReloadKey((current) => current + 1),
  });

  useEffect(() => {
    let active = true;

    setState({
      data: null,
      loading: true,
      error: null,
      reload: () => setReloadKey((current) => current + 1),
    });

    loader()
      .then((data) => {
        if (!active) {
          return;
        }
        setState({
          data,
          loading: false,
          error: null,
          reload: () => setReloadKey((current) => current + 1),
        });
      })
      .catch((error: Error) => {
        if (!active) {
          return;
        }
        setState({
          data: null,
          loading: false,
          error: error.message,
          reload: () => setReloadKey((current) => current + 1),
        });
      });

    return () => {
      active = false;
    };
  }, [...deps, reloadKey]);

  return state;
}
