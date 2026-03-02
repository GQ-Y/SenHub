import { useState, useCallback, useRef } from 'react';

export type ModalType = 'info' | 'success' | 'warning' | 'error';

export interface ModalOptions {
  title?: string;
  message: string;
  type?: ModalType;
  confirmText?: string;
  cancelText?: string;
  onConfirm?: () => void;
  onCancel?: () => void;
}

export interface ConfirmOptions {
  title?: string;
  message: string;
  onConfirm: () => void;
  onCancel?: () => void;
  confirmText?: string;
  cancelText?: string;
}

export const useModal = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [modalOptions, setModalOptions] = useState<ModalOptions | null>(null);
  const [isConfirm, setIsConfirm] = useState(false);
  const closeTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showModal = useCallback((options: ModalOptions) => {
    if (closeTimeoutRef.current) {
      clearTimeout(closeTimeoutRef.current);
      closeTimeoutRef.current = null;
    }
    setModalOptions(options);
    setIsConfirm(false);
    setIsOpen(true);
  }, []);

  const showConfirm = useCallback((options: ConfirmOptions) => {
    if (closeTimeoutRef.current) {
      clearTimeout(closeTimeoutRef.current);
      closeTimeoutRef.current = null;
    }
    setModalOptions({
      ...options,
      type: 'warning',
      showCancel: true,
    });
    setIsConfirm(true);
    setIsOpen(true);
  }, []);

  const closeModal = useCallback(() => {
    setIsOpen(false);
    if (closeTimeoutRef.current) clearTimeout(closeTimeoutRef.current);
    closeTimeoutRef.current = setTimeout(() => {
      setModalOptions(null);
      closeTimeoutRef.current = null;
    }, 300);
  }, []);

  return {
    isOpen,
    modalOptions,
    isConfirm,
    showModal,
    showConfirm,
    closeModal,
  };
};
