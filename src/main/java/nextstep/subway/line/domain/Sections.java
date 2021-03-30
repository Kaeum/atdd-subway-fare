package nextstep.subway.line.domain;

import nextstep.subway.auth.infrastructure.SecurityContextHolder;
import nextstep.subway.station.domain.Station;
import org.springframework.util.ObjectUtils;

import javax.persistence.CascadeType;
import javax.persistence.Embeddable;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import java.util.*;

@Embeddable
public class Sections {
    private static final int BASE_FARE = 1250;
    private static final int ADDITIONAL_FARE_PER_DISTANCE = 100;
    private static final int MAX_UNTIL_FIFTY = 800;

    private static final int TEN = 10;
    private static final int FIFTY = 50;

    private static final int ONE = 1;
    private static final int EIGHT = 8;
    private static final int FIVE = 5;
    private static final int ELEVEN = 11;
    private static final int FIFTY_ONE = 51;

    @OneToMany(mappedBy = "line", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Section> sections = new ArrayList<>();

    public Sections() {

    }

    public Sections(List<Section> sections) {
        this.sections = sections;
    }

    public List<Section> getSections() {
        return sections;
    }

    public List<Station> getStations() {
        if (sections.isEmpty()) {
            return Arrays.asList();
        }

        List<Station> stations = new ArrayList<>();
        Station downStation = findUpStation();
        stations.add(downStation);

        while (downStation != null) {
            Station finalDownStation = downStation;
            Optional<Section> nextLineStation = sections.stream()
                    .filter(it -> it.getUpStation() == finalDownStation)
                    .findFirst();
            if (!nextLineStation.isPresent()) {
                break;
            }
            downStation = nextLineStation.get().getDownStation();
            stations.add(downStation);
        }

        return stations;
    }

    private Station findUpStation() {
        Station downStation = sections.get(0).getUpStation();
        while (downStation != null) {
            Station finalDownStation = downStation;
            Optional<Section> nextLineStation = sections.stream()
                    .filter(it -> it.getDownStation() == finalDownStation)
                    .findFirst();
            if (!nextLineStation.isPresent()) {
                break;
            }
            downStation = nextLineStation.get().getUpStation();
        }

        return downStation;
    }

    public void addSection(Section section) {
        boolean isUpStationExisted = getStations().stream().anyMatch(it -> it == section.getUpStation());
        boolean isDownStationExisted = getStations().stream().anyMatch(it -> it == section.getDownStation());
        checkValidation(section, isUpStationExisted, isDownStationExisted);

        if (isUpStationExisted) {
            updateUpSection(section);
        }

        if (isDownStationExisted) {
            updateDownSection(section);
        }

        sections.add(section);
    }

    private void updateDownSection(Section section) {
        sections.stream()
                .filter(it -> it.getDownStation() == section.getDownStation())
                .findFirst()
                .ifPresent(it -> it.updateDownStation(section.getUpStation(), section.getDistance(), section.getDuration()));
    }

    private void updateUpSection(Section section) {
        sections.stream()
                .filter(it -> it.getUpStation() == section.getUpStation())
                .findFirst()
                .ifPresent(it -> it.updateUpStation(section.getDownStation(), section.getDistance(), section.getDuration()));
    }

    private void checkValidation(Section section, boolean isUpStationExisted, boolean isDownStationExisted) {
        if (isUpStationExisted && isDownStationExisted) {
            throw new RuntimeException("이미 등록된 구간 입니다.");
        }

        if (!sections.isEmpty() && getStations().stream().noneMatch(it -> it == section.getUpStation()) &&
                getStations().stream().noneMatch(it -> it == section.getDownStation())) {
            throw new RuntimeException("등록할 수 없는 구간 입니다.");
        }
    }

    public void removeSection(Station station) {
        if (sections.size() <= 1) {
            throw new RuntimeException();
        }

        Optional<Section> upLineStation = sections.stream()
                .filter(it -> it.getUpStation() == station)
                .findFirst();
        Optional<Section> downLineStation = sections.stream()
                .filter(it -> it.getDownStation() == station)
                .findFirst();

        if (upLineStation.isPresent() && downLineStation.isPresent()) {
            Station newUpStation = downLineStation.get().getUpStation();
            Station newDownStation = upLineStation.get().getDownStation();
            int newDistance = upLineStation.get().getDistance() + downLineStation.get().getDistance();
            int newDuration = upLineStation.get().getDuration() + downLineStation.get().getDuration();
            sections.add(new Section(upLineStation.get().getLine(), newUpStation, newDownStation, newDistance, newDuration));
        }

        upLineStation.ifPresent(it -> sections.remove(it));
        downLineStation.ifPresent(it -> sections.remove(it));
    }

    public int getTotalDistance() {
        return sections.stream().mapToInt(it -> it.getDistance()).sum();
    }

    public int getTotalDuration() {
        return sections.stream().mapToInt(it -> it.getDuration()).sum();
    }

    public int getTotalFare(int distance) {
        int fareByDistance = BASE_FARE;

        if(distance <= TEN) {
            return fareByDistance;
        }

        if(distance > FIFTY) {
            fareByDistance += MAX_UNTIL_FIFTY + (int) ((Math.ceil((distance - FIFTY_ONE) / EIGHT) + ONE) * ADDITIONAL_FARE_PER_DISTANCE);
            return discountByAge(addLineFare(fareByDistance));
        }

        fareByDistance += (int) ((Math.ceil((distance - ELEVEN) / FIVE) + ONE) * ADDITIONAL_FARE_PER_DISTANCE);
        return discountByAge(addLineFare(fareByDistance));
    }

    private int addLineFare(int fare) {
        int additionalLineFare = sections.stream()
                .map(Section::getLine)
                .mapToInt(Line::getAdditionalLineFare)
                .max()
                .orElse(0);

        return fare + additionalLineFare;
    }

    private int discountByAge(int fare) {
        int userAge = getUserAge();

        if(6 <= userAge && userAge < 13) {
            return (( fare - 350 ) * FIVE / TEN);
        }

        if(13 <= userAge && userAge < 20) {
            return (( fare - 350) * EIGHT / TEN);
        }

        return fare;
    }

    private int getUserAge() {
        if(!ObjectUtils.isEmpty(SecurityContextHolder.getContext().getAuthentication())) {
            Map<String, String> principal = (Map) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return Integer.parseInt(principal.get("age"));
        }
        return -1;
    }


}
