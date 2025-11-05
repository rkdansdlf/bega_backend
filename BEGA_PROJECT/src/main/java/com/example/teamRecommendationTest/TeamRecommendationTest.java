package com.example.teamRecommendationTest;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor
public class TeamRecommendationTest {

	@Id
	@Column(name = "team_id")
	private String teamId;
	
	@Column(name = "team_name", nullable = false)
    private String teamName;
	
	@Column(name = "team_short_name", nullable = false)
	private String teamShortName;
	
	@Column(name = "color") 
    private String color;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "team_profiles", joinColumns = @JoinColumn(name = "team_id"))
    @Column(name = "profile", nullable = false)
    private List<String> profiles = new ArrayList<>();
    
    public TeamRecommendationTest(String teamId, String teamName, String teamShortName) {
    	this.teamId = teamId;
    	this.teamName = teamName;
    	this.teamShortName = teamShortName;
    	}
    
}
