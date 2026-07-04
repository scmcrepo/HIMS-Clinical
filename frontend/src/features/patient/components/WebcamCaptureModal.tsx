import { useEffect, useRef, useState } from 'react';
import { Modal } from '../../../components/ui/Modal';
import { Camera, RefreshCw, Check, Loader2, VideoOff, X } from 'lucide-react';
import { cn } from '../../../lib/utils';

interface WebcamCaptureModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCapture: (file: File) => void;
}

export default function WebcamCaptureModal({ isOpen, onClose, onCapture }: WebcamCaptureModalProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const [hasPermission, setHasPermission] = useState<boolean | null>(null);
  const [isInitializing, setIsInitializing] = useState<boolean>(true);
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [shutterFlash, setShutterFlash] = useState<boolean>(false);

  // Initialize camera when modal opens
  const startCamera = async () => {
    setIsInitializing(true);
    setErrorMsg(null);
    setCapturedImage(null);
    setHasPermission(null);

    // Make sure to clean up any existing stream first
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 640 },
          height: { ideal: 480 },
          facingMode: 'user',
        },
        audio: false,
      });

      streamRef.current = stream;
      setHasPermission(true);

      // Give a tiny timeout for video element ref to attach
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      }, 50);
    } catch (err: any) {
      console.error('Error accessing webcam:', err);
      setHasPermission(false);
      if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
        setErrorMsg('Webcam access was denied. Please allow camera permissions in your browser settings.');
      } else if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
        setErrorMsg('No camera found on your system. Please connect a webcam.');
      } else {
        setErrorMsg(err.message || 'Could not access webcam. Please check connections.');
      }
    } finally {
      setIsInitializing(false);
    }
  };

  // Close stream helper
  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
  };

  useEffect(() => {
    if (isOpen) {
      startCamera();
    } else {
      stopCamera();
    }

    return () => {
      stopCamera();
    };
  }, [isOpen]);

  const handleCapture = () => {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas) return;

    // Trigger visual shutter flash micro-animation
    setShutterFlash(true);
    setTimeout(() => setShutterFlash(false), 150);

    const context = canvas.getContext('2d');
    if (!context) return;

    const videoWidth = video.videoWidth || 640;
    const videoHeight = video.videoHeight || 480;

    // Target a 1:1 square crop centered in the video
    const cropSize = Math.min(videoWidth, videoHeight);
    const startX = (videoWidth - cropSize) / 2;
    const startY = (videoHeight - cropSize) / 2;

    canvas.width = cropSize;
    canvas.height = cropSize;

    // Mirroring horizontal to match the mirror preview on capture
    context.translate(cropSize, 0);
    context.scale(-1, 1);

    // Draw cropped video frame
    context.drawImage(
      video,
      startX,
      startY,
      cropSize,
      cropSize, // source crop coords & dimensions
      0,
      0,
      cropSize,
      cropSize // destination canvas coords & dimensions
    );

    // Reset transform
    context.setTransform(1, 0, 0, 1, 0, 0);

    const dataUrl = canvas.toDataURL('image/jpeg', 0.9);
    setCapturedImage(dataUrl);

    // Stop camera feed tracks since we have captured the picture
    stopCamera();
  };

  const handleRetake = () => {
    setCapturedImage(null);
    startCamera();
  };

  const handleUsePhoto = () => {
    if (!capturedImage) return;

    // Convert base64 DataURL back to Blob & File
    fetch(capturedImage)
      .then((res) => res.blob())
      .then((blob) => {
        const file = new File([blob], `patient_captured_${Date.now()}.jpg`, {
          type: 'image/jpeg',
        });
        onCapture(file);
        onClose();
      })
      .catch((err) => {
        console.error('Error generating image file from capture:', err);
      });
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Take Patient Photo" size="lg">
      <div className="flex flex-col bg-neutral-900 text-white rounded-2xl overflow-hidden shadow-2xl h-[520px]">
        {/* Header Section */}
        <div className="px-5 py-4 border-b border-neutral-800 flex items-center justify-between bg-neutral-950">
          <div className="flex items-center gap-2">
            <div className={cn("w-2 h-2 rounded-full", capturedImage ? "bg-amber-500 animate-pulse" : "bg-emerald-500 animate-pulse")} />
            <h3 className="text-sm font-bold tracking-wide uppercase text-neutral-300">
              {capturedImage ? 'Review Photo' : 'Live Camera Feed'}
            </h3>
          </div>
          <button 
            type="button"
            onClick={onClose}
            className="text-neutral-400 hover:text-white p-1 hover:bg-neutral-800 rounded-lg transition-colors cursor-pointer"
          >
            <X size={18} />
          </button>
        </div>

        {/* Viewfinder Content Area */}
        <div className="relative flex-1 bg-neutral-950 flex items-center justify-center overflow-hidden">
          
          {/* Shutter Flash Animation overlay */}
          <div 
            className={cn(
              "absolute inset-0 bg-white z-40 pointer-events-none transition-opacity duration-150 ease-out",
              shutterFlash ? "opacity-90" : "opacity-0"
            )} 
          />

          {isInitializing && (
            <div className="flex flex-col items-center gap-3 text-neutral-400">
              <Loader2 className="w-8 h-8 animate-spin text-neutral-500" />
              <p className="text-xs font-semibold uppercase tracking-wider animate-pulse">Starting webcam...</p>
            </div>
          )}

          {errorMsg && (
            <div className="flex flex-col items-center text-center p-6 max-w-sm text-neutral-400 space-y-4">
              <div className="w-12 h-12 bg-red-950/55 border border-red-800 rounded-full flex items-center justify-center text-red-500 shadow-lg">
                <VideoOff className="w-6 h-6" />
              </div>
              <p className="text-sm font-medium leading-relaxed">{errorMsg}</p>
              <button
                type="button"
                onClick={startCamera}
                className="px-4 py-2 bg-neutral-805 hover:bg-neutral-700 text-white rounded-xl text-xs font-bold transition-all shadow-md active:scale-95 border border-neutral-800"
              >
                Retry Camera
              </button>
            </div>
          )}

          {/* Live Camera View Mode */}
          {hasPermission && !capturedImage && (
            <div className="relative w-full h-full flex items-center justify-center">
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                className="w-full h-full object-cover scale-x-[-1]" // mirror effect
              />
              
              {/* Premium High-Tech Focus Overlay & Masking */}
              <div className="absolute inset-0 bg-neutral-950/40 pointer-events-none flex items-center justify-center">
                
                {/* Circular Mask Highlight */}
                <div className="w-64 h-64 rounded-full border-2 border-white/60 shadow-[0_0_0_9999px_rgba(10,10,10,0.65)] relative flex items-center justify-center">
                  
                  {/* Camera Focus Brackets */}
                  <div className="absolute inset-[-12px] border-t-2 border-l-2 border-emerald-400 w-6 h-6 rounded-tl-lg" />
                  <div className="absolute inset-y-[-12px] right-[-12px] border-t-2 border-r-2 border-emerald-400 w-6 h-6 rounded-tr-lg" />
                  <div className="absolute bottom-[-12px] left-[-12px] border-b-2 border-l-2 border-emerald-400 w-6 h-6 rounded-bl-lg" />
                  <div className="absolute bottom-[-12px] right-[-12px] border-b-2 border-r-2 border-emerald-400 w-6 h-6 rounded-br-lg" />
                  
                  {/* Center Dot indicator */}
                  <div className="w-1.5 h-1.5 bg-emerald-400/80 rounded-full animate-ping" />
                  
                  <span className="absolute -bottom-8 text-[10px] font-bold tracking-widest text-emerald-400 uppercase drop-shadow">
                    Align Patient Head
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* Picture Confirmation / Review Mode */}
          {capturedImage && (
            <div className="relative w-full h-full flex flex-col items-center justify-center p-4 bg-neutral-950">
              <div className="w-64 h-64 rounded-full overflow-hidden border-4 border-neutral-800 shadow-2xl relative">
                <img 
                  src={capturedImage} 
                  alt="Captured patient preview" 
                  className="w-full h-full object-cover" 
                />
              </div>
              <div className="mt-6 text-center space-y-1 z-10">
                <h4 className="text-base font-bold text-white tracking-tight">Is this picture okay?</h4>
                <p className="text-xs text-neutral-400">Ensure patient is clearly visible and centered.</p>
              </div>
            </div>
          )}
        </div>

        {/* Action Controls Section */}
        <div className="p-5 bg-neutral-950 border-t border-neutral-900 flex justify-center items-center">
          
          {/* Live stream captures button */}
          {hasPermission && !capturedImage && (
            <button
              type="button"
              onClick={handleCapture}
              className="group flex items-center justify-center gap-2.5 px-6 py-3.5 bg-white text-neutral-950 hover:bg-neutral-100 rounded-full font-bold text-sm transition-all shadow-xl active:scale-95 duration-150"
            >
              <Camera size={18} className="text-neutral-950 group-hover:scale-110 transition-transform" />
              Capture Photo
            </button>
          )}

          {/* Confirmation buttons */}
          {capturedImage && (
            <div className="flex items-center gap-4 w-full max-w-sm">
              <button
                type="button"
                onClick={handleRetake}
                className="flex-1 flex items-center justify-center gap-2 px-5 py-3 border border-neutral-800 hover:border-neutral-700 bg-neutral-900 hover:bg-neutral-800 text-neutral-300 hover:text-white rounded-xl text-sm font-bold transition-all active:scale-95"
              >
                <RefreshCw size={16} />
                Retake
              </button>
              <button
                type="button"
                onClick={handleUsePhoto}
                className="flex-1 flex items-center justify-center gap-2 px-5 py-3 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-sm font-bold transition-all shadow-lg shadow-emerald-950/20 active:scale-95"
              >
                <Check size={16} />
                Use Photo
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Hidden capture canvas */}
      <canvas ref={canvasRef} className="hidden" />
    </Modal>
  );
}
