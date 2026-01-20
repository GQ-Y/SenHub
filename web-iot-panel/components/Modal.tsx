import React, { useEffect } from 'react';
import { X, AlertCircle, CheckCircle, Info, AlertTriangle } from 'lucide-react';

export type ModalType = 'info' | 'success' | 'warning' | 'error';

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  message: string;
  type?: ModalType;
  confirmText?: string;
  cancelText?: string;
  onConfirm?: () => void;
  onCancel?: () => void;
  showCancel?: boolean;
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  message,
  type = 'info',
  confirmText = '确定',
  cancelText = '取消',
  onConfirm,
  onCancel,
  showCancel = false,
}) => {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const handleConfirm = () => {
    if (onConfirm) {
      onConfirm();
    }
    onClose();
  };

  const handleCancel = () => {
    if (onCancel) {
      onCancel();
    }
    onClose();
  };

  const getIcon = () => {
    const iconClass = "w-6 h-6";
    switch (type) {
      case 'success':
        return <CheckCircle className={`${iconClass} text-green-500`} />;
      case 'warning':
        return <AlertTriangle className={`${iconClass} text-yellow-500`} />;
      case 'error':
        return <AlertCircle className={`${iconClass} text-red-500`} />;
      default:
        return <Info className={`${iconClass} text-blue-500`} />;
    }
  };

  const getButtonColors = () => {
    switch (type) {
      case 'success':
        return 'bg-green-600 hover:bg-green-700';
      case 'warning':
        return 'bg-yellow-600 hover:bg-yellow-700';
      case 'error':
        return 'bg-red-600 hover:bg-red-700';
      default:
        return 'bg-blue-600 hover:bg-blue-700';
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center">
      {/* 背景遮罩 */}
      <div
        className="fixed inset-0 bg-black/50 backdrop-blur-sm"
        onClick={onClose}
      />
      
      {/* 弹窗内容 */}
      <div className="relative bg-white rounded-2xl shadow-2xl max-w-md w-full mx-4 z-[101] transform transition-all">
        {/* 头部 */}
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <div className="flex items-center space-x-3">
            {getIcon()}
            <h3 className="text-lg font-semibold text-gray-800">
              {title || (type === 'success' ? '成功' : type === 'warning' ? '警告' : type === 'error' ? '错误' : '提示')}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-1 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <X size={20} className="text-gray-500" />
          </button>
        </div>

        {/* 内容 */}
        <div className="p-6">
          <p className="text-gray-700 whitespace-pre-wrap">{message}</p>
        </div>

        {/* 底部按钮 */}
        <div className="flex items-center justify-end space-x-3 p-6 border-t border-gray-200">
          {showCancel && (
            <button
              onClick={handleCancel}
              className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors font-medium"
            >
              {cancelText}
            </button>
          )}
          <button
            onClick={handleConfirm}
            className={`px-4 py-2 text-white rounded-lg transition-colors font-medium ${getButtonColors()}`}
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
};

// 确认对话框组件
export interface ConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  message: string;
  onConfirm: () => void;
  onCancel?: () => void;
  confirmText?: string;
  cancelText?: string;
}

export const ConfirmModal: React.FC<ConfirmModalProps> = ({
  isOpen,
  onClose,
  title = '确认',
  message,
  onConfirm,
  onCancel,
  confirmText = '确定',
  cancelText = '取消',
}) => {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      message={message}
      type="warning"
      confirmText={confirmText}
      cancelText={cancelText}
      showCancel={true}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  );
};
