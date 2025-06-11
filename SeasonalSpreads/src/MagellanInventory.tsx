import React, { useState, useRef, useEffect } from "react";
import { FaUpload, FaFileExcel, FaSpinner, FaChartLine } from "react-icons/fa";
import MagellanChart from "./MagellanChart";

interface MagellanInventoryProps {
  // Add any props you might need
}

const MagellanInventory: React.FC<MagellanInventoryProps> = () => {
  const [activeTab, setActiveTab] = useState<"graph" | "upload">("graph");
  const [selectedFuel, setSelectedFuel] = useState("A");
  const [file, setFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadMessage, setUploadMessage] = useState("");
  const [isLoadingSheet, setIsLoadingSheet] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  interface DateInfo {
    original: string;
    nextDay: string;
    originalISO: string;
    nextDayISO: string;
  }

  const [lastDate, setLastDate] = useState<DateInfo>({
    original: "",
    nextDay: "",
    originalISO: "",
    nextDayISO: "",
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selectedFile = e.target.files[0];
      if (selectedFile.type === "application/pdf") {
        setFile(selectedFile);
        setUploadMessage("");
      } else {
        setUploadMessage("Please upload a PDF file");
        setFile(null);
      }
    }
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const droppedFile = e.dataTransfer.files[0];
      if (droppedFile.type === "application/pdf") {
        setFile(droppedFile);
        setUploadMessage("");
      } else {
        setUploadMessage("Please upload a PDF file");
        setFile(null);
      }
    }
  };

  const handleUpload = async () => {
    if (!file) {
      setUploadMessage("Please select PDF files first");
      return;
    }

    setIsUploading(true);
    setUploadMessage("");

    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await fetch(
        "https://rioseasonalspreads-production.up.railway.app/upload-inventory",
        {
          method: "POST",
          body: formData,
        }
      );

      if (!response.ok) {
        const error = await response.text();
        throw new Error(`Failed to upload: ${error}`);
      }

      setUploadMessage("Successfully processed file");
      await handleGetLastDate();
    } catch (error) {
      setUploadMessage(
        error instanceof Error ? error.message : "Upload failed"
      );
    } finally {
      setIsUploading(false);
    }
  };

  const handleGetLastDate = async () => {
    try {
      const response = await fetch(
        "https://rioseasonalspreads-production.up.railway.app/getLatestDate"
      );
      if (!response.ok) {
        throw new Error("Failed to fetch last updated date");
      }
      const data = await response.json();

      const originalDate = new Date(data.lastUpdated);
      const nextDay = new Date(originalDate);
      nextDay.setDate(originalDate.getDate() + 1);

      const formatOptions: Intl.DateTimeFormatOptions = {
        year: "numeric",
        month: "long",
        day: "numeric",
      };

      setLastDate({
        original: originalDate.toLocaleDateString("en-US", formatOptions),
        nextDay: nextDay.toLocaleDateString("en-US", formatOptions),
        originalISO: originalDate.toISOString().split("T")[0],
        nextDayISO: nextDay.toISOString().split("T")[0],
      });
    } catch (error) {
      console.error("Error fetching last date:", error);
      setLastDate({
        original: "Error fetching date",
        nextDay: "",
        originalISO: "",
        nextDayISO: "",
      });
    }
  };

  const handleGetSheet = async () => {
    setIsLoadingSheet(true);
    try {
      const response = await fetch(
        "https://rioseasonalspreads-production.up.railway.app/get-inventory-sheet"
      );
      if (!response.ok) {
        throw new Error("Failed to fetch spreadsheet");
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "inventory_data.xlsx";
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      a.remove();
    } catch (error) {
      setUploadMessage(
        error instanceof Error
          ? error.message
          : "Failed to download spreadsheet"
      );
    } finally {
      setIsLoadingSheet(false);
    }
  };

  useEffect(() => {
    handleGetLastDate();
  }, []);

  return (
    <div
      style={{
        maxWidth: "1200px",
        margin: "0 auto",
        padding: "20px",
        fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
      }}
    >
      <h2
        style={{
          textAlign: "center",
          color: "#2c3e50",
          marginBottom: "20px",
        }}
      >
        Magellan Inventory Data
      </h2>

      {/* Tab Navigation */}
      <div
        style={{
          display: "flex",
          justifyContent: "center",
          gap: "10px",
          marginBottom: "30px",
        }}
      >
        <button
          onClick={() => setActiveTab("graph")}
          style={{
            padding: "10px 20px",
            border: "none",
            borderRadius: "4px",
            background: activeTab === "graph" ? "#3498db" : "#ecf0f1",
            color: activeTab === "graph" ? "white" : "#2c3e50",
            cursor: "pointer",
            fontWeight: "bold",
            transition: "all 0.3s ease",
            display: "flex",
            alignItems: "center",
            gap: "8px",
          }}
        >
          <FaChartLine />
          Inventory Graphs
        </button>
        <button
          onClick={() => setActiveTab("upload")}
          style={{
            padding: "10px 20px",
            border: "none",
            borderRadius: "4px",
            background: activeTab === "upload" ? "#3498db" : "#ecf0f1",
            color: activeTab === "upload" ? "white" : "#2c3e50",
            cursor: "pointer",
            fontWeight: "bold",
            transition: "all 0.3s ease",
            display: "flex",
            alignItems: "center",
            gap: "8px",
          }}
        >
          <FaUpload />
          Data Upload
        </button>
      </div>

      {lastDate.original && (
        <div
          style={{
            textAlign: "center",
            marginBottom: "20px",
            fontSize: "0.9rem",
            color: "#666",
          }}
        >
          <div style={{ marginTop: "5px" }}>
            <strong>Last Uploaded Report:</strong> {lastDate.nextDay}
          </div>
        </div>
      )}

      {activeTab === "graph" ? (
        <>
          {/* Fuel Type Selector */}
          <div
            style={{
              display: "flex",
              justifyContent: "center",
              gap: "10px",
              marginBottom: "20px",
              flexWrap: "wrap",
            }}
          >
            {["A", "E", "Q", "V", "X", "Y"].map((fuel) => (
              <button
                key={fuel}
                onClick={() => setSelectedFuel(fuel)}
                style={{
                  padding: "8px 16px",
                  border: "none",
                  borderRadius: "4px",
                  background: selectedFuel === fuel ? "#3498db" : "#ecf0f1",
                  color: selectedFuel === fuel ? "white" : "#2c3e50",
                  cursor: "pointer",
                  fontWeight: "bold",
                  transition: "all 0.3s ease",
                }}
              >
                {
                  {
                    A: "A PREMIUM",
                    E: "E DENATURED",
                    Q: "Q COMMERICAL JET FUEL",
                    V: "V SUB-OCTANE",
                    X: "X #2 ULSD",
                    Y: "Y #1 ULSD",
                  }[fuel]
                }
              </button>
            ))}
          </div>

          {/* Chart Component */}
          <MagellanChart fuelType={selectedFuel} />
        </>
      ) : (
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: "20px",
            marginBottom: "30px",
          }}
        >
          {/* File Upload Section */}
          <div
            onDragOver={handleDragOver}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            style={{
              border: "2px dashed #bdc3c7",
              borderRadius: "8px",
              padding: "40px",
              textAlign: "center",
              cursor: "pointer",
              backgroundColor: file ? "#e8f5e9" : "#f8f9fa",
              transition: "all 0.3s ease",
            }}
          >
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              accept=".pdf"
              style={{ display: "none" }}
            />
            {file ? (
              <>
                <p style={{ fontWeight: "bold", marginBottom: "10px" }}>
                  {file.name}
                </p>
                <p>Click to select a different PDF</p>
              </>
            ) : (
              <>
                <FaUpload size={48} style={{ marginBottom: "10px" }} />
                <p style={{ fontWeight: "bold", marginBottom: "10px" }}>
                  Drag & drop PDF files, or click to select
                </p>
                <p>Only PDF files are accepted</p>
              </>
            )}
          </div>

          {uploadMessage && (
            <p
              style={{
                color: uploadMessage.includes("success")
                  ? "#2e7d32"
                  : "#c62828",
                textAlign: "center",
              }}
            >
              {uploadMessage}
            </p>
          )}

          <div
            style={{
              display: "flex",
              justifyContent: "center",
              gap: "20px",
              marginTop: "20px",
            }}
          >
            <button
              onClick={handleUpload}
              disabled={!file || isUploading}
              style={{
                padding: "12px 24px",
                backgroundColor: "#2e7d32",
                color: "white",
                border: "none",
                borderRadius: "4px",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                gap: "8px",
                fontWeight: "bold",
                opacity: !file || isUploading ? 0.6 : 1,
              }}
            >
              {isUploading ? (
                <>
                  <FaSpinner className="spin" />
                  Processing...
                </>
              ) : (
                <>
                  <FaUpload />
                  Update Data
                </>
              )}
            </button>

            <button
              onClick={handleGetSheet}
              disabled={isLoadingSheet}
              style={{
                padding: "12px 24px",
                backgroundColor: "#1565c0",
                color: "white",
                border: "none",
                borderRadius: "4px",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                gap: "8px",
                fontWeight: "bold",
                opacity: isLoadingSheet ? 0.6 : 1,
              }}
            >
              {isLoadingSheet ? (
                <>
                  <FaSpinner className="spin" />
                  Loading...
                </>
              ) : (
                <>
                  <FaFileExcel />
                  Get Spreadsheet
                </>
              )}
            </button>
          </div>
        </div>
      )}

      <style>
        {`
          @keyframes spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
          }
          .spin {
            animation: spin 1s linear infinite;
          }
        `}
      </style>
    </div>
  );
};

export default MagellanInventory;
