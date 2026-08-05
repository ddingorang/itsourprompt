import {
  createContext,
  useContext,
  useEffect,
  useState,
  type Dispatch,
  type ReactNode,
  type SetStateAction,
} from 'react';

export type ColorMode = 'dark' | 'light';

const COLOR_MODE_STORAGE_KEY = 'prompt-practice-color-mode';

interface ThemeContextValue {
  colorMode: ColorMode;
  setColorMode: Dispatch<SetStateAction<ColorMode>>;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function getInitialColorMode(): ColorMode {
  if (typeof window === 'undefined') {
    return 'dark';
  }

  return window.localStorage.getItem(COLOR_MODE_STORAGE_KEY) === 'light'
    ? 'light'
    : 'dark';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [colorMode, setColorMode] = useState<ColorMode>(getInitialColorMode);

  useEffect(() => {
    document.documentElement.dataset.colorMode = colorMode;
    document.documentElement.style.colorScheme = colorMode;
    window.localStorage.setItem(COLOR_MODE_STORAGE_KEY, colorMode);
  }, [colorMode]);

  return (
    <ThemeContext.Provider value={{ colorMode, setColorMode }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);

  if (!context) {
    throw new Error('useTheme은 ThemeProvider 안에서 사용해야 합니다.');
  }

  return context;
}
