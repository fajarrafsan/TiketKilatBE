package com.projekan.tiket_pesawat.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.projekan.tiket_pesawat.dto.PenerbanganResponseDto;
import com.projekan.tiket_pesawat.dto.PenerbanganUpdateDto;
import com.projekan.tiket_pesawat.exception.StatusTidakValidException;
import com.projekan.tiket_pesawat.exception.TidakDitemukanException;
import com.projekan.tiket_pesawat.models.Penerbangan;
import com.projekan.tiket_pesawat.models.UpdatePenerbanganHistory;
import com.projekan.tiket_pesawat.repository.PenerbanganRepository;
import com.projekan.tiket_pesawat.repository.UpdatePenerbanganHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final PenerbanganRepository penerbanganRepository;
    private final UpdatePenerbanganHistoryRepository updatePenerbanganHistoryRepository;

    @Override
    public Map<String, Object> updatePenerbangan(Penerbangan penerbangan, PenerbanganUpdateDto request,
            String userNya) {

        List<UpdatePenerbanganHistory> histories = new ArrayList<>();

        Penerbangan salinDataLama = salinanDataPenerbangan(penerbangan);
        Map<String, Object> perubahan = new HashMap<>();

        if (request.getKotaKeberangkatan() != null && request.getKotaTujuan() != null &&
                request.getKotaKeberangkatan().equalsIgnoreCase(request.getKotaTujuan())) {
            throw new IllegalArgumentException("Kota keberangkatan dan tujuan tidak boleh di tempat yang sama");
        }

        if (request.getWaktuKeberangkatan() != null && request.getWaktuKedatangan() != null &&
                !request.getWaktuKedatangan().isAfter(request.getWaktuKeberangkatan())) {
            throw new IllegalArgumentException("Waktu tiba harus setelah waktu keberangkatan");
        }
        Map<String, String> dataBaru = new HashMap<>();
        Map<String, String> dataLama = new HashMap<>();

        dataBaru.put("id", String.valueOf(penerbangan.getId()));
        dataLama.put("id", String.valueOf(penerbangan.getId()));

        if (request.getMaskapai() != null && !penerbangan.getMaskapai().equals(request.getMaskapai())) {
            histories.add(
                    buatHistory(penerbangan, userNya, "Maskapai", salinDataLama.getMaskapai(), request.getMaskapai()));
            dataBaru.put("maskapai", request.getMaskapai());
            dataLama.put("maskapai", salinDataLama.getMaskapai());
            penerbangan.setMaskapai(request.getMaskapai());
        }
        if (request.getKotaKeberangkatan() != null
                && !penerbangan.getKotaKeberangkatan().equals(request.getKotaKeberangkatan())) {
            histories.add(
                    buatHistory(penerbangan, userNya, "Kota keberangkatan", salinDataLama.getKotaKeberangkatan(),
                            request.getKotaKeberangkatan()));
            dataBaru.put("kotaKeberangkatan", request.getKotaKeberangkatan());
            dataLama.put("kotaKeberangkatan", salinDataLama.getKotaKeberangkatan());
            penerbangan.setKotaKeberangkatan(request.getKotaKeberangkatan());
        }
        if (request.getKotaTujuan() != null && !penerbangan.getKotaTujuan().equals(request.getKotaTujuan())) {
            histories.add(
                    buatHistory(penerbangan, userNya, "Kota Tujuan", salinDataLama.getKotaTujuan(),
                            request.getKotaTujuan()));
            dataBaru.put("kotaTujuan", request.getKotaTujuan());
            dataLama.put("kotaTujuan", salinDataLama.getKotaTujuan());
            penerbangan.setKotaTujuan(request.getKotaTujuan());
        }
        if (request.getWaktuKeberangkatan() != null
                && !penerbangan.getWaktuKeberangkatan().equals(request.getWaktuKeberangkatan())) {
            histories.add(
                    buatHistory(penerbangan, userNya, "Waktu Keberangkatan",
                            salinDataLama.getWaktuKeberangkatan().toString(),
                            request.getWaktuKeberangkatan().toString()));
            dataBaru.put("WaktuKeberangkatan", request.getWaktuKeberangkatan().toString());
            dataLama.put("WaktuKeberangkatan", salinDataLama.getWaktuKeberangkatan().toString());
            penerbangan.setWaktuKeberangkatan(request.getWaktuKeberangkatan());
        }
        if (request.getWaktuKedatangan() != null
                && !penerbangan.getWaktuKedatangan().equals(request.getWaktuKedatangan())) {
            histories.add(
                    buatHistory(penerbangan, userNya, "Waktu Kedatangan", salinDataLama.getWaktuKedatangan().toString(),
                            request.getWaktuKedatangan().toString()));
            dataBaru.put("WaktuKedatangan", request.getWaktuKedatangan().toString());
            dataLama.put("WaktuKedatangan", salinDataLama.getWaktuKedatangan().toString());
            penerbangan.setWaktuKedatangan(request.getWaktuKedatangan());
        }
        if (request.getHargaTiket() != null && !penerbangan.getHargaTiket().equals(request.getHargaTiket())) {
            histories.add(
                    buatHistory(penerbangan, userNya, "Harga Tiket", salinDataLama.getHargaTiket().toString(),
                            request.getHargaTiket().toString()));
            dataBaru.put("hargaTiket", String.valueOf(request.getHargaTiket()));
            dataLama.put("hargaTiket", String.valueOf(salinDataLama.getHargaTiket()));
            penerbangan.setHargaTiket(request.getHargaTiket());
        }
        if (request.getKursi() != null && !penerbangan.getKursi().equals(request.getKursi())) {
            histories.add(
                    buatHistory(penerbangan, userNya, "Jumlah Kursi", salinDataLama.getKursi().toString(),
                            request.getKursi().toString()));
            dataBaru.put("kursi", String.valueOf(request.getKursi()));
            dataLama.put("kursi", String.valueOf(salinDataLama.getKursi()));
            penerbangan.setKursi(request.getKursi());
        }
        perubahan.put("dataBaru", dataBaru);
        perubahan.put("dataLama", dataLama);
        updatePenerbanganHistoryRepository.saveAll(histories);
        penerbanganRepository.save(penerbangan);
        return perubahan;

    }

    private Penerbangan salinanDataPenerbangan(Penerbangan p) {
        Penerbangan salinan = Penerbangan.builder()
                .id(p.getId())
                .maskapai(p.getMaskapai())
                .kotaKeberangkatan(p.getKotaKeberangkatan())
                .kotaTujuan(p.getKotaTujuan())
                .waktuKeberangkatan(p.getWaktuKeberangkatan())
                .waktuKedatangan(p.getWaktuKedatangan())
                .hargaTiket(p.getHargaTiket())
                .kursi(p.getKursi()).build();
        return salinan;
    }

    private UpdatePenerbanganHistory buatHistory(Penerbangan penerbangan, String pengUpdate, String fieldYangDiubah,
            String dataLama, String dataBaru) {
        return UpdatePenerbanganHistory.builder()
                .penerbangan(penerbangan)
                .pengUpdate(pengUpdate)
                .waktuUpdate(LocalDateTime.now())
                .fieldYangDiubah(fieldYangDiubah)
                .dataLama(dataLama)
                .dataBaru(dataBaru)
                .build();
    }

    @Override
    public ByteArrayInputStream eksportKeExcell(Penerbangan penerbangan) throws IOException {

        List<UpdatePenerbanganHistory> histories = updatePenerbanganHistoryRepository.findByPenerbangan(penerbangan);

        if (histories.isEmpty()) {
            throw new StatusTidakValidException(
                    "Tidak History Update Pada Penerbangan dengan ID " + penerbangan.getId());
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream();) {

            Sheet sheet = workbook.createSheet("Data Update History");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            setAllBorders(headerStyle);

            CellStyle datasStyle = workbook.createCellStyle();
            setAllBorders(datasStyle);

            CellStyle tanggalStyle = workbook.createCellStyle();
            CreationHelper buatCreationTanggal = workbook.getCreationHelper();
            tanggalStyle.setDataFormat(buatCreationTanggal.createDataFormat().getFormat("yyyy-mm-dd HH-mm"));
            setAllBorders(tanggalStyle);

            String[] columHeader = { "Tanggal Update", "Diubah Oleh", "Kolom Yang Di Ubah", "Sebelumnya",
                    "Setelahnya" };
            Row rowHeader = sheet.createRow(0);
            for (int i = 0; i < columHeader.length; i++) {
                Cell cell = rowHeader.createCell(i);
                cell.setCellValue(columHeader[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (UpdatePenerbanganHistory history : histories) {
                Row row = sheet.createRow(rowIndex++);

                Cell tanggalCell = row.createCell(0);
                tanggalCell.setCellValue(history.getWaktuUpdate());
                tanggalCell.setCellStyle(tanggalStyle);

                Cell pengUpdateCell = row.createCell(1);
                pengUpdateCell.setCellValue(history.getPengUpdate());
                pengUpdateCell.setCellStyle(datasStyle);

                Cell fieldYangDiubahCell = row.createCell(2);
                fieldYangDiubahCell.setCellValue(history.getFieldYangDiubah());
                fieldYangDiubahCell.setCellStyle(datasStyle);

                Cell dataLamaCell = row.createCell(3);
                dataLamaCell.setCellValue(history.getDataLama());
                dataLamaCell.setCellStyle(datasStyle);

                Cell dataBaruCell = row.createCell(4);
                dataBaruCell.setCellValue(history.getDataBaru());
                dataBaruCell.setCellStyle(datasStyle);
            }

            for (int i = 0; i < columHeader.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);
            return new ByteArrayInputStream(output.toByteArray());
        }
    }

    private void setAllBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    public Page<Penerbangan> ambilDataPenerbangan(int page, int size, String urutBerdasarkan, String arahSorting) {

        if (page < 0) {
            throw new IllegalArgumentException("Input Halaman Tidak Boleh negatif.");
        }

        if (size <= 0) {
            throw new IllegalArgumentException("Ukuran data per halaman harus lebih dari 0.");
        }

        if (!isiFieldYangValid(urutBerdasarkan)) {
            throw new IllegalArgumentException("Kolom urutan tidak valid: " + urutBerdasarkan);
        }

        if (!arahSortingYangValid(arahSorting)) {
            throw new IllegalArgumentException("Arah pengurutan hanya boleh 'asc' atau 'desc' aja.");
        }

        Sort urutan = arahSorting.equalsIgnoreCase("desc")
                ? Sort.by(urutBerdasarkan).descending()
                : Sort.by(urutBerdasarkan).ascending();
        Pageable paging = PageRequest.of(page, size, urutan);
        Page<Penerbangan> hasil = penerbanganRepository.findAll(paging);

        if (hasil.isEmpty()) {
            throw new TidakDitemukanException("Data Penerbangan Kosong atau tidak di temukan.");
        }

        return hasil;
    }

    public final List<String> ISI_FIELD_VALID = Arrays.asList("maskapai", "kotaKeberangkatan", "kotaTujuan",
            "waktuKeberangkatan", "waktuKedatangan", "hargaTiket", "kursi");

    public boolean isiFieldYangValid(String sortingBy) {
        return ISI_FIELD_VALID.contains(sortingBy);
    }

    public boolean arahSortingYangValid(String arahSorting) {
        return arahSorting.equalsIgnoreCase("asc") || arahSorting.equalsIgnoreCase("desc");
    }

    @Override
    public List<PenerbanganResponseDto> ambilPenerbanganTersedia(String dari, String ke, LocalDate tanggal, String maskapai){
        List<Penerbangan> listPenerbangan = penerbanganRepository.findFiltered(dari, ke, tanggal, maskapai);

        return listPenerbangan.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
    }

    private PenerbanganResponseDto mapToResponse(Penerbangan p) {
        return PenerbanganResponseDto.builder()
                .id(p.getId())
                .maskapai(p.getMaskapai())
                .kotaKeberangkatan(p.getKotaKeberangkatan())
                .kotaTujuan(p.getKotaTujuan())
                .waktuKeberangkatan(p.getWaktuKeberangkatan())
                .waktuKedatangan(p.getWaktuKedatangan())
                .hargaTiket(p.getHargaTiket())
                .build();
    }
}
