import { useState, useCallback } from 'react';

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

  const showModal = useCallback((options: ModalOptions) => {
    setModalOptions(options);
    setIsConfirm(false);
    setIsOpen(true);
  }, []);

  const showConfirm = useCallback((options: ConfirmOptions) => {
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
    // 延迟清除选项，等待动画完成
    setTimeout(() => {
      setModalOptions(null);
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
