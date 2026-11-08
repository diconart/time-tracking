package md.dipro.worktime.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "project_tasks")
public class ProjectTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int estimatedMinutes;

    protected ProjectTask() { }

    public ProjectTask(Project project, String description, int estimatedMinutes) {
        this.project = project;
        this.description = description;
        this.estimatedMinutes = estimatedMinutes;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getDescription() { return description; }
    public int getEstimatedMinutes() { return estimatedMinutes; }
}
