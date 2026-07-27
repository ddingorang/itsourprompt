export const buttonStyles = `
  .app-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    min-height: 44px;
    padding: 0 18px;
    border: 1px solid transparent;
    font: 800 12px/1 var(--font-sans);
    letter-spacing: -.01em;
    text-decoration: none;
    cursor: pointer;
    transition: background .18s ease, border-color .18s ease, color .18s ease,
      opacity .18s ease;
  }

  .app-button--primary {
    border-color: var(--accent);
    background: var(--accent);
    color: var(--black);
  }

  .app-button--secondary {
    border-color: #4b4b4b;
    background: transparent;
    color: var(--white);
  }

  .app-button--ghost {
    border-color: #535353;
    background: transparent;
    color: var(--white);
  }

  .app-button--full {
    width: 100%;
  }

  .app-button--primary:hover:not(:disabled):not([aria-disabled='true']),
  .app-button--primary:focus-visible:not(:disabled):not([aria-disabled='true']) {
    outline: none;
    background: transparent;
    color: var(--accent);
  }

  .app-button--secondary:hover:not(:disabled):not([aria-disabled='true']),
  .app-button--secondary:focus-visible:not(:disabled):not([aria-disabled='true']),
  .app-button--ghost:hover:not(:disabled):not([aria-disabled='true']),
  .app-button--ghost:focus-visible:not(:disabled):not([aria-disabled='true']) {
    border-color: var(--accent);
    outline: none;
    background: var(--accent);
    color: var(--black);
  }

  .app-button:disabled,
  .app-button[aria-disabled='true'] {
    cursor: not-allowed;
    opacity: .45;
  }
`;
