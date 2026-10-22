package com.example.voting.service;

import com.example.voting.dto.Dtos.UploadSummary;
import com.example.voting.entity.EligibleVoter;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.EligibleVoterRepository;
import com.example.voting.util.CsvUtil;
import com.example.voting.util.EmailUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VoterListService {
    private final EligibleVoterRepository eligible;
    private final ElectionService electionService;

    /** CSV columns: email,name,roll_number (header row optional). Emails are normalized and de-duplicated. */
    @Transactional
    public UploadSummary importCsv(Long electionId, MultipartFile file) {
        var election = electionService.get(electionId);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a CSV file to upload.");

        // Resolve allowed domains for this specific election
        var allowedDomains = electionService.effectiveDomains(election);

        int added = 0, duplicates = 0, invalid = 0;
        List<String> invalidRows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<EligibleVoter> toSave = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) { line = line.replace("\uFEFF", ""); }
                if (line.isBlank()) { first = false; continue; }
                List<String> cells = CsvUtil.parseLine(line);
                String email = EmailUtil.normalize(cells.get(0));
                if (first && "email".equals(email)) { first = false; continue; }
                first = false;

                boolean domainOk = allowedDomains.stream()
                        .anyMatch(d -> EmailUtil.hasDomain(email, d));
                if (!EmailUtil.isValid(email) || !domainOk) {
                    invalid++;
                    if (invalidRows.size() < 10) invalidRows.add(line.length() > 80 ? line.substring(0, 80) : line);
                    continue;
                }
                if (!seen.add(email) || eligible.existsByElectionIdAndEmail(electionId, email)) { duplicates++; continue; }
                String name = cells.size() > 1 && !cells.get(1).isBlank() ? cells.get(1) : null;
                String roll = cells.size() > 2 && !cells.get(2).isBlank() ? cells.get(2) : null;
                toSave.add(new EligibleVoter(electionId, email, name, roll));
                added++;
            }
        } catch (IOException ex) {
            throw ApiException.badRequest("Could not read that file.");
        }
        eligible.saveAll(toSave);
        return new UploadSummary(added, duplicates, invalid, invalidRows);
    }
}
